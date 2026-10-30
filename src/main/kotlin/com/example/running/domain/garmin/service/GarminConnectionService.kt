package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.controller.dto.GarminAuthorizationResponse
import com.example.running.domain.garmin.controller.dto.GarminConnectionResponse
import com.example.running.domain.garmin.repository.*
import com.example.running.domain.garmin.service.dto.GarminTokens
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.GarminApiException
import com.example.running.exception.GarminRequestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class GarminConnectionService(
    private val properties: GarminProperties,
    private val repository: GarminRepository,
    private val users: UserRepository,
    private val configurations: UserConfigurationService,
    private val cipher: GarminTokenCipher,
    private val api: GarminApiClient,
    private val transaction: TransactionTemplate,
) {
    private val appReturnUris = mapOf(
        "app" to "app://user/activity-connection",
        "runtaeho-dev" to "runtaeho-dev://user/activity-connection",
    )
    private val stateNoncePattern = Regex("[A-Za-z0-9_-]{43}")

    fun connection(userId: Long): GarminConnectionResponse {
        requireActive(userId)
        val value = repository.connection(userId)
        return GarminConnectionResponse(
            properties.enabled,
            value?.status ?: GarminConnectionStatus.DISCONNECTED,
            value?.connectedAt,
            value?.lastReceivedAt,
            value?.lastImportedAt,
            value?.lastImportedCount ?: 0,
            value?.activityPermission ?: false,
        )
    }

    fun authorize(userId: Long, appScheme: String? = null): GarminAuthorizationResponse {
        requireEnabled()
        if (appScheme != null && appScheme !in appReturnUris) throw GarminRequestException(400)
        val nonce = GarminPkce.state()
        // Hash the app target together with the nonce so callbacks cannot switch app profiles.
        val state = if (appScheme == null) nonce else "${appScheme}_$nonce"
        val verifier = GarminPkce.verifier()
        val hash = GarminPkce.stateHash(state)
        val expires = Instant.now().plusSeconds(600)
        transaction.executeWithoutResult {
            lock(userId)
            if (repository.connection(userId)?.status == GarminConnectionStatus.DISCONNECTING) throw GarminRequestException(409)
            repository.deleteStates(userId)
            repository.insertState(
                GarminOAuthState(hash, userId, cipher.encrypt(verifier, "state:$hash"), expires)
            )
        }
        return GarminAuthorizationResponse(
            api.authorizationUrl(state, GarminPkce.challenge(verifier)),
            state,
            expires,
        )
    }

    // State consumption commits before contacting Garmin, so failed callbacks cannot be replayed.
    fun callback(state: String, code: String?, error: String?): String {
        requireEnabled()
        appReturnUri(state)
        val hash = GarminPkce.stateHash(state)
        val claimed = transaction.execute { repository.consumeState(hash, Instant.now()) } ?: throw GarminRequestException(400)
        if (error != null || code.isNullOrBlank()) return appReturn(state, "cancelled")
        if (code.length > 4096) return appReturn(state, "failed")
        return try {
            transaction.executeWithoutResult {
                lock(claimed.userId)
                // Disconnect/withdrawal/new authorization invalidates even a callback already in flight.
                if (repository.state(hash) == null || !claimed.expiresAt.isAfter(Instant.now())) throw GarminRequestException(400)
                val tokens = api.exchangeCode(code, cipher.decrypt(claimed.verifier, "state:$hash"))
                val garminId = api.userId(tokens.accessToken)
                val permission = "ACTIVITY_EXPORT" in api.permissions(tokens.accessToken)
                val previous = repository.connection(claimed.userId)
                val owner = repository.connectionByGarminId(garminId)
                if (owner != null && owner.userId != claimed.userId) throw GarminRequestException(409)
                if (previous?.externalUserId != null && previous.externalUserId != garminId) throw GarminRequestException(409)
                saveTokens(
                    claimed.userId,
                    garminId,
                    tokens,
                    permission,
                    previous?.connectedAt ?: Instant.now(),
                )
                repository.deleteStates(claimed.userId)
            }
            appReturn(state, "connected")
        } catch (error: Exception) {
            // The controller never receives provider error bodies, authorization codes, or token values.
            LoggerFactory.getLogger(javaClass).warn("Garmin callback failed ({})", error.javaClass.simpleName)
            appReturn(state, "failed")
        }
    }

    fun disconnect(userId: Long): GarminConnectionResponse {
        requireEnabled()
        transaction.executeWithoutResult {
            lock(userId)
            stopImport(userId)
        }
        maintain(userId)
        return connection(userId)
    }

    // Also called inside the existing withdrawal transaction. Remote revocation is retried by the worker.
    fun stopImport(userId: Long) {
        configurations.getOrCreateForUpdate(userId)
        repository.deleteStates(userId)
        if (repository.connection(userId)?.externalUserId != null) {
            repository.setStatus(userId, GarminConnectionStatus.DISCONNECTING)
        }
    }

    fun maintain(userId: Long) {
        transaction.executeWithoutResult {
            configurations.getOrCreateForUpdate(userId)
            val value = repository.connection(userId) ?: return@executeWithoutResult
            if (value.status != GarminConnectionStatus.CONNECTED && value.status != GarminConnectionStatus.DISCONNECTING) return@executeWithoutResult
            val disconnecting = value.status == GarminConnectionStatus.DISCONNECTING
            repository.maintenanceAttempt(userId, Instant.now())
            try {
                if (disconnecting && value.accessToken != null && value.accessExpiresAt?.isAfter(Instant.now()) == true) {
                    try {
                        api.deregister(cipher.decrypt(value.accessToken, "connection:$userId:access"))
                        repository.clearConnection(userId)
                        return@executeWithoutResult
                    } catch (error: GarminApiException) {
                        if (error.statusCode != 401) throw error
                    }
                }
                if (disconnecting || value.accessToken == null || value.accessExpiresAt?.isAfter(Instant.now().plusSeconds(600)) != true) {
                    if (value.refreshExpiresAt?.isAfter(Instant.now()) == false) {
                        throw GarminApiException(401, credentialsInvalid = true)
                    }
                    val refresh = value.refreshToken?.let { cipher.decrypt(it, "connection:$userId:refresh") }
                        ?: throw GarminApiException(401, credentialsInvalid = true)
                    val tokens = api.refresh(refresh)
                    // Persist every rotated refresh token in this same serialized transaction.
                    saveTokens(
                        userId,
                        checkNotNull(value.externalUserId),
                        tokens,
                        value.activityPermission,
                        checkNotNull(value.connectedAt),
                    )
                    if (disconnecting) {
                        repository.setStatus(userId, GarminConnectionStatus.DISCONNECTING)
                        api.deregister(tokens.accessToken)
                        repository.clearConnection(userId)
                    }
                }
            } catch (error: GarminApiException) {
                if (error.credentialsInvalid) {
                    if (disconnecting) {
                        repository.clearConnection(userId)
                    } else {
                        repository.setStatus(userId, GarminConnectionStatus.RECONNECT_REQUIRED)
                    }
                }
                // Keep a successfully rotated refresh token committed even if subsequent revocation fails.
                LoggerFactory.getLogger(javaClass).warn(
                    "Garmin maintenance request failed (providerStatus={}, credentialsInvalid={})",
                    error.statusCode,
                    error.credentialsInvalid,
                )
            }
        }
    }

    private fun saveTokens(
        userId: Long,
        garminId: String,
        tokens: GarminTokens,
        permission: Boolean,
        connectedAt: Instant,
    ) {
        val now = Instant.now()
        repository.saveConnection(
            GarminConnection(
                userId,
                garminId,
                if (permission) GarminConnectionStatus.CONNECTED else GarminConnectionStatus.RECONNECT_REQUIRED,
                cipher.encrypt(tokens.accessToken, "connection:$userId:access"),
                cipher.encrypt(tokens.refreshToken, "connection:$userId:refresh"),
                now.plusSeconds(tokens.expiresIn),
                tokens.refreshExpiresIn?.let(now::plusSeconds),
                permission,
                connectedAt,
                null,
                null,
                0,
            )
        )
    }

    private fun lock(userId: Long) {
        configurations.getOrCreateForUpdate(userId)
        requireActive(userId)
    }

    private fun requireActive(userId: Long) {
        val user = users.findById(userId).orElseThrow { GarminRequestException(401) }
        if (!user.isEnabled || user.isDeleted) throw GarminRequestException(401)
    }

    private fun requireEnabled() {
        if (!properties.enabled) throw GarminRequestException(503)
    }

    private fun appReturnUri(state: String): String {
        // Existing clients and in-flight requests did not include an app scheme.
        if (stateNoncePattern.matches(state)) return "runtaeho://user/activity-connection"
        val parts = state.split('_', limit = 2)
        if (parts.size != 2 || !stateNoncePattern.matches(parts[1])) throw GarminRequestException(400)
        return appReturnUris[parts[0]] ?: throw GarminRequestException(400)
    }

    private fun appReturn(state: String, result: String) =
        "${appReturnUri(state)}?garminResult=$result&state=$state"
}
