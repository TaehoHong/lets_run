package com.example.running.domain.garmin.service

import com.example.running.domain.garmin.repository.GarminConnectionStatus
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.service.RunningImportService
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.GarminApiException
import com.example.running.exception.RunningValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.TransactionDefinition
import java.time.Instant
import java.time.ZoneOffset

@Service
class GarminInboxWorker(
    private val repository: GarminRepository,
    private val cipher: GarminTokenCipher,
    private val mapper: ObjectMapper,
    private val activities: GarminActivityMapper,
    private val imports: RunningImportService,
    private val users: UserRepository,
    private val configurations: UserConfigurationService,
    private val api: GarminApiClient,
    private val transaction: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val activityTypes = GarminWebhookPayload.activityTypes
    // Connection reads precede the user lock; later balance/league reads must see its latest commit.
    private val processingTransaction = TransactionTemplate(checkNotNull(transaction.transactionManager), transaction).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    fun processOne(): Boolean {
        var claimedId: Long? = null
        return try {
            processingTransaction.execute {
                val inbox = repository.nextInbox(Instant.now()) ?: return@execute false
                claimedId = inbox.id
                require(inbox.payload.length <= GarminInputLimits.MAX_ENCRYPTED_LENGTH) {
                    "Garmin payload exceeds limit"
                }
                val payload = cipher.decrypt(inbox.payload, "inbox:${inbox.hash}")
                require(payload.toByteArray(Charsets.UTF_8).size <= GarminInputLimits.MAX_BYTES) {
                    "Garmin payload exceeds limit"
                }
                val root = GarminWebhookPayload.parse(mapper, payload)
                val entries = root.fields().asSequence().toList()
                val mapped = entries.filter { it.key in activityTypes }.flatMap { entry ->
                    entry.value.mapNotNull { activity ->
                        activities.map(activity)?.let { it to (entry.key == "manuallyUpdatedActivities") }
                    }
                }
                val controls = entries.filter { it.key !in activityTypes }.flatMap { it.value.toList() }
                val externalIds = (mapped.map { it.first.garminUserId } + controls.map {
                    val id = it.path("userId").asText()
                    require(id.isNotBlank() && id.length <= 255) { "Invalid Garmin event" }
                    id
                }).distinct()
                val connections = externalIds.mapNotNull(repository::connectionByGarminId).associateBy { it.userId }
                connections.keys.sorted().forEach { configurations.getOrCreateForUpdate(it) }
                val active = connections.values.mapNotNull { original ->
                    val current = repository.connection(original.userId) ?: return@mapNotNull null
                    val user = users.findById(current.userId).orElse(null) ?: return@mapNotNull null
                    if (!user.isEnabled || user.isDeleted || current.status != GarminConnectionStatus.CONNECTED ||
                        current.connectedAt == null || inbox.createdAt.isBefore(current.connectedAt)) {
                        return@mapNotNull null
                    }
                    current.externalUserId?.let { it to current }
                }.toMap()
                // Notifications carry no connection generation. Confirm current permission before applying them.
                controls.forEach { event ->
                    active[event.path("userId").asText()]?.let { value ->
                        val token = cipher.decrypt(checkNotNull(value.accessToken), "connection:${value.userId}:access")
                        val granted = try {
                            "ACTIVITY_EXPORT" in api.permissions(token)
                        } catch (error: GarminApiException) {
                            if (error.statusCode !in setOf(400, 401, 403)) throw error
                            false
                        }
                        if (!granted) repository.setStatus(value.userId, GarminConnectionStatus.RECONNECT_REQUIRED)
                    }
                }
                mapped.forEach { (activity, manuallyUpdated) ->
                    val value = active[activity.garminUserId] ?: return@forEach
                    if (repository.connection(value.userId)?.status != GarminConnectionStatus.CONNECTED) return@forEach
                    repository.received(value.userId, inbox.createdAt)
                    val result = imports.importGarmin(
                        value.userId,
                        ImportRunningBatchRequest(listOf(activity.record)),
                        checkNotNull(value.connectedAt).atOffset(ZoneOffset.UTC),
                        activity.manual || manuallyUpdated,
                        receivedAt = inbox.createdAt,
                    )
                    repository.imported(value.userId, Instant.now(), result.createdCount + result.updatedCount)
                }
                repository.completeInbox(inbox.id, Instant.now())
                true
            } ?: false
        } catch (error: Exception) {
            claimedId?.let { id ->
                transaction.executeWithoutResult {
                    if (error is RunningValidationException) repository.completeInbox(id, Instant.now())
                    else repository.failInbox(id, Instant.now())
                }
            }
            if (error is RunningValidationException) logger.warn("Garmin inbox rejected ({})", error.reason.name)
            else logger.warn("Garmin inbox deferred ({})", error.javaClass.simpleName)
            claimedId != null
        }
    }
}
