package com.example.running.domain.garmin.repository

import com.example.running.domain.activity.repository.ActivityConnection
import com.example.running.domain.activity.repository.ActivityConnectionStatus
import com.example.running.domain.activity.repository.ActivityInbox
import com.example.running.domain.activity.repository.ActivityOAuthState
import com.example.running.domain.activity.repository.ActivityProviderRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

typealias GarminConnectionStatus = ActivityConnectionStatus
typealias GarminConnection = ActivityConnection
typealias GarminOAuthState = ActivityOAuthState
typealias GarminInbox = ActivityInbox

@Repository
class GarminRepository(jdbc: JdbcTemplate) {
    private val activityProviderRepository = ActivityProviderRepository(jdbc, "GARMIN")

    fun connection(userId: Long): GarminConnection? = activityProviderRepository.connection(userId)

    fun connectionByExternalId(id: String): GarminConnection? = activityProviderRepository.connectionByExternalId(id)

    fun connectionByGarminId(id: String): GarminConnection? = activityProviderRepository.connectionByExternalId(id)

    fun saveConnection(value: GarminConnection) = activityProviderRepository.saveConnection(value)

    fun setStatus(userId: Long, status: GarminConnectionStatus) = activityProviderRepository.setStatus(userId, status)

    fun clearConnection(userId: Long) = activityProviderRepository.clearConnection(userId)

    fun received(userId: Long, at: Instant) = activityProviderRepository.received(userId, at)

    fun imported(userId: Long, at: Instant, importedCount: Int) =
        activityProviderRepository.imported(userId, at, importedCount)

    fun dueConnections(now: Instant): List<Long> = activityProviderRepository.dueConnections(now)

    fun maintenanceAttempt(userId: Long, now: Instant) = activityProviderRepository.maintenanceAttempt(userId, now)

    fun insertState(state: GarminOAuthState) = activityProviderRepository.insertState(state)

    fun consumeState(hash: String, now: Instant): GarminOAuthState? = activityProviderRepository.consumeState(hash, now)

    fun state(hash: String): GarminOAuthState? = activityProviderRepository.state(hash)

    fun deleteStates(userId: Long) = activityProviderRepository.deleteStates(userId)

    fun enqueue(hash: String, payload: String, now: Instant) = activityProviderRepository.enqueue(hash, payload, now)

    fun nextInbox(now: Instant): GarminInbox? = activityProviderRepository.nextInbox(now)

    fun completeInbox(id: Long, now: Instant) = activityProviderRepository.completeInbox(id, now)

    fun failInbox(id: Long, now: Instant) = activityProviderRepository.failInbox(id, now)

    fun cleanup(now: Instant) = activityProviderRepository.cleanup(now)
}
