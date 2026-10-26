package com.example.running.domain.activity.repository

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class ActivityConnectionStatus { DISCONNECTED, CONNECTED, RECONNECT_REQUIRED, DISCONNECTING }

// These objects deliberately have no generated toString: they contain encrypted credentials/payloads.
class ActivityConnection(
    val userId: Long, val externalUserId: String?, val status: ActivityConnectionStatus,
    val accessToken: String?, val refreshToken: String?,
    val accessExpiresAt: Instant?, val refreshExpiresAt: Instant?, val activityPermission: Boolean,
    val connectedAt: Instant?, val lastReceivedAt: Instant?, val lastImportedAt: Instant?, val lastImportedCount: Int,
)
class ActivityOAuthState(val hash: String, val userId: Long, val verifier: String, val expiresAt: Instant)
class ActivityInbox(val id: Long, val hash: String, val payload: String, val attempts: Int, val createdAt: Instant = Instant.EPOCH)

open class ActivityProviderRepository(private val jdbc: JdbcTemplate, private val provider: String) {
    fun connection(userId: Long): ActivityConnection? = jdbc.query(
        "SELECT c.*, k.access_token_encrypted, k.refresh_token_encrypted, k.access_expires_at, k.refresh_expires_at FROM activity_connection c LEFT JOIN activity_oauth_credential k ON k.connection_id = c.id WHERE c.provider = ? AND c.user_id = ?",
        { rs, _ -> connection(rs) }, provider, userId).firstOrNull()

    fun connectionByExternalId(id: String): ActivityConnection? = jdbc.query(
        "SELECT user_id FROM activity_connection WHERE provider = ? AND external_user_id = ?",
        { rs, _ -> rs.getLong("user_id") }, provider, id).firstOrNull()?.let(::connection)

    fun saveConnection(value: ActivityConnection) {
        ensureConnection(value.userId)
        jdbc.update("""UPDATE activity_connection SET external_user_id = ?, status = ?, connected_at = ?
            WHERE provider = ? AND user_id = ?""", value.externalUserId, value.status.name,
            value.connectedAt?.let(::utc), provider, value.userId)
        jdbc.update("""INSERT INTO activity_oauth_credential
            (connection_id, access_token_encrypted, refresh_token_encrypted, access_expires_at, refresh_expires_at)
            SELECT id, ?, ?, ?, ? FROM activity_connection WHERE provider = ? AND user_id = ?
            ON DUPLICATE KEY UPDATE access_token_encrypted = VALUES(access_token_encrypted),
            refresh_token_encrypted = VALUES(refresh_token_encrypted), access_expires_at = VALUES(access_expires_at),
            refresh_expires_at = VALUES(refresh_expires_at)""", checkNotNull(value.accessToken), checkNotNull(value.refreshToken),
            utc(checkNotNull(value.accessExpiresAt)), value.refreshExpiresAt?.let(::utc), provider, value.userId)
    }

    private fun ensureConnection(userId: Long) {
        jdbc.update("INSERT INTO activity_connection (user_id, provider, status) VALUES (?, ?, 'DISCONNECTED') ON DUPLICATE KEY UPDATE id = id", userId, provider)
    }

    fun setStatus(userId: Long, status: ActivityConnectionStatus) {
        jdbc.update("UPDATE activity_connection SET status = ? WHERE provider = ? AND user_id = ?", status.name, provider, userId)
    }

    fun clearConnection(userId: Long) {
        jdbc.update("DELETE k FROM activity_oauth_credential k JOIN activity_connection c ON c.id = k.connection_id WHERE c.provider = ? AND c.user_id = ?", provider, userId)
        jdbc.update("""UPDATE activity_connection SET status = 'DISCONNECTED', external_user_id = NULL, connected_at = NULL,
            oauth_state_hash = NULL, oauth_verifier_encrypted = NULL, oauth_expires_at = NULL, oauth_used_at = NULL
            WHERE provider = ? AND user_id = ?""", provider, userId)
    }

    fun received(userId: Long, at: Instant) {
        jdbc.update("UPDATE activity_connection SET last_received_at = IF(last_received_at IS NULL OR last_received_at < ?, ?, last_received_at) WHERE provider = ? AND user_id = ?",
            utc(at), utc(at), provider, userId)
    }

    fun imported(userId: Long, at: Instant, importedCount: Int) {
        jdbc.update("""UPDATE activity_connection SET last_imported_at = IF(? > 0, ?, last_imported_at),
            last_imported_count = ? WHERE provider = ? AND user_id = ?""",
            importedCount, utc(at), importedCount, provider, userId)
    }

    fun dueConnections(now: Instant): List<Long> = jdbc.queryForList("""
        SELECT c.user_id FROM activity_connection c LEFT JOIN activity_oauth_credential k ON k.connection_id = c.id
        WHERE c.provider = ? AND (c.status = 'DISCONNECTING' OR (c.status = 'CONNECTED' AND k.access_expires_at <= ?))
        ORDER BY COALESCE(c.maintenance_attempted_at, '1970-01-01'), c.user_id LIMIT 100
    """.trimIndent(), Long::class.java, provider, utc(now.plusSeconds(600)))

    fun maintenanceAttempt(userId: Long, now: Instant) {
        jdbc.update("UPDATE activity_connection SET maintenance_attempted_at = ? WHERE provider = ? AND user_id = ?", utc(now), provider, userId)
    }

    fun insertState(state: ActivityOAuthState) {
        ensureConnection(state.userId)
        jdbc.update("""UPDATE activity_connection SET oauth_state_hash = ?, oauth_verifier_encrypted = ?,
            oauth_expires_at = ?, oauth_used_at = NULL WHERE provider = ? AND user_id = ?""",
            state.hash, state.verifier, utc(state.expiresAt), provider, state.userId)
    }

    fun consumeState(hash: String, now: Instant): ActivityOAuthState? {
        if (jdbc.update("""UPDATE activity_connection SET oauth_used_at = ? WHERE provider = ? AND oauth_state_hash = ?
            AND oauth_used_at IS NULL AND oauth_expires_at > ?""", utc(now), provider, hash, utc(now)) != 1) return null
        return state(hash)
    }

    fun state(hash: String): ActivityOAuthState? = jdbc.query("SELECT * FROM activity_connection WHERE provider = ? AND oauth_state_hash = ?",
        { rs, _ -> ActivityOAuthState(rs.getString("oauth_state_hash"), rs.getLong("user_id"), rs.getString("oauth_verifier_encrypted"),
            checkNotNull(rs.instant("oauth_expires_at"))) }, provider, hash).firstOrNull()

    fun deleteStates(userId: Long) {
        jdbc.update("""UPDATE activity_connection SET oauth_state_hash = NULL, oauth_verifier_encrypted = NULL,
            oauth_expires_at = NULL, oauth_used_at = NULL WHERE provider = ? AND user_id = ?""", provider, userId)
    }

    fun enqueue(hash: String, payload: String, now: Instant) {
        jdbc.update("""INSERT INTO activity_webhook_inbox (provider, event_hash, payload_encrypted, available_at, created_at)
            VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE event_hash = VALUES(event_hash)""",
            provider, hash, payload, utc(now), utc(now))
    }

    fun nextInbox(now: Instant): ActivityInbox? = jdbc.query("""
        SELECT * FROM activity_webhook_inbox WHERE provider = ? AND status = 'PENDING' AND available_at <= ?
        ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
    """.trimIndent(), { rs, _ -> ActivityInbox(rs.getLong("id"), rs.getString("event_hash"),
        rs.getString("payload_encrypted"), rs.getInt("attempts"), checkNotNull(rs.instant("created_at"))) }, provider, utc(now)).firstOrNull()

    fun completeInbox(id: Long, now: Instant) {
        jdbc.update("UPDATE activity_webhook_inbox SET status = 'DONE', payload_encrypted = NULL, processed_at = ? WHERE provider = ? AND id = ?",
            utc(now), provider, id)
    }

    fun failInbox(id: Long, now: Instant) {
        jdbc.update("""UPDATE activity_webhook_inbox SET attempts = attempts + 1,
            available_at = ?, status = IF(attempts >= 10, 'FAILED', 'PENDING'),
            payload_encrypted = IF(attempts >= 10, NULL, payload_encrypted),
            processed_at = IF(attempts >= 10, ?, processed_at)
            WHERE provider = ? AND id = ? AND status = 'PENDING'""", utc(now.plusSeconds(300)), utc(now), provider, id)
    }

    fun cleanup(now: Instant) {
        jdbc.update("UPDATE activity_connection SET oauth_state_hash = NULL, oauth_verifier_encrypted = NULL, oauth_expires_at = NULL, oauth_used_at = NULL WHERE provider = ? AND oauth_expires_at < ?", provider, utc(now))
        jdbc.update("DELETE FROM activity_webhook_inbox WHERE provider = ? AND processed_at < ?", provider, utc(now.minusSeconds(30 * 86400L)))
    }

    private fun utc(value: Instant): LocalDateTime = LocalDateTime.ofInstant(value, ZoneOffset.UTC)
    private fun ResultSet.instant(column: String): Instant? = getObject(column, LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)

    private fun connection(rs: ResultSet) = ActivityConnection(rs.getLong("user_id"), rs.getString("external_user_id"),
        ActivityConnectionStatus.valueOf(rs.getString("status")), rs.getString("access_token_encrypted"), rs.getString("refresh_token_encrypted"),
        rs.instant("access_expires_at"), rs.instant("refresh_expires_at"),
        rs.getString("status") == "CONNECTED", rs.instant("connected_at"),
        rs.instant("last_received_at"), rs.instant("last_imported_at"),
        rs.getInt("last_imported_count"))
}
