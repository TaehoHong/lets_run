package com.example.running.domain.notification.repository

import com.example.running.domain.notification.repository.NotificationRepository.Companion.instant
import com.example.running.domain.notification.repository.NotificationRepository.Companion.utc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class PushCandidate(val id: Long, val status: String)
data class PushJob(
    val id: Long, val notificationId: Long, val userId: Long, val deviceId: Long, val version: Long,
    val sourceKey: String, val type: NotificationType, val token: String?, val language: String,
    val ticketId: String?, val attemptCount: Int, val created: Instant, val validBinding: Boolean,
)

@Repository
class NotificationPushRepository(private val jdbc: JdbcTemplate) {
    fun candidates(now: Instant): List<PushCandidate> = jdbc.query("""
        SELECT id, status FROM notification_push WHERE status IN ('PENDING', 'RECEIPT_PENDING')
            AND next_attempt_datetime <= ? ORDER BY next_attempt_datetime, id LIMIT 100
    """.trimIndent(), { rs, _ -> PushCandidate(rs.getLong(1), rs.getString(2)) }, utc(now))

    fun recoverExpired(now: Instant) {
        jdbc.update("""
            UPDATE notification_push SET status = IF(provider_ticket_id IS NULL, 'PENDING', 'RECEIPT_PENDING'),
                lease_token = NULL, locked_until_datetime = NULL, next_attempt_datetime = ?
            WHERE status = 'SENDING' AND locked_until_datetime <= ?
        """.trimIndent(), utc(now), utc(now))
    }

    fun claim(candidate: PushCandidate, lease: String, now: Instant): Boolean = jdbc.update("""
        UPDATE notification_push SET status = 'SENDING', lease_token = ?, locked_until_datetime = ?
        WHERE id = ? AND status = ? AND next_attempt_datetime <= ?
    """.trimIndent(), lease, utc(now.plusSeconds(60)), candidate.id, candidate.status, utc(now)) == 1

    fun beginSend(id: Long, lease: String, anchor: Instant?): Boolean = jdbc.update("""
        UPDATE notification_push p
        JOIN notification n ON n.id = p.notification_id
        JOIN user_device d ON d.id = p.user_device_id
        JOIN user u ON u.id = n.user_id
        JOIN user_notification_configuration c ON c.user_id = n.user_id
        LEFT JOIN user_running_reminder_state r ON r.user_id = n.user_id
        SET p.attempt_count = p.attempt_count + 1
        WHERE p.id = ? AND p.status = 'SENDING' AND p.lease_token = ?
            AND p.locked_until_datetime > UTC_TIMESTAMP() AND p.attempt_count < 5
            AND d.user_id = n.user_id AND d.binding_version = p.binding_version
            AND d.is_enabled = 1 AND d.push_token IS NOT NULL
            AND u.is_enabled = 1 AND u.is_deleted = 0 AND n.expired_datetime > UTC_TIMESTAMP()
            AND c.push_enabled = 1
            AND CASE n.type
                WHEN 'LEAGUE_RESULT' THEN c.league_push_enabled
                WHEN 'RUNNING_REMINDER' THEN c.reminder_push_enabled
                WHEN 'NOTICE' THEN c.notice_push_enabled
                ELSE 0 END = 1
            AND (n.type <> 'RUNNING_REMINDER' OR r.anchor_datetime = ?)
    """.trimIndent(), id, lease, anchor?.let(::utc)) == 1

    fun claimed(id: Long, lease: String, now: Instant): PushJob? = jdbc.query("""
        SELECT p.*, n.user_id, n.type, n.source_key, d.push_token,
            COALESCE(ul.code, dl.code) AS language,
            (d.user_id = n.user_id AND d.binding_version = p.binding_version AND d.is_enabled = 1
                AND u.is_enabled = 1 AND u.is_deleted = 0 AND n.expired_datetime > ?) AS valid_binding
        FROM notification_push p JOIN notification n ON n.id = p.notification_id
        JOIN user_device d ON d.id = p.user_device_id JOIN user u ON u.id = n.user_id
        JOIN language dl ON dl.id = d.language_id
        LEFT JOIN user_configuration uc ON uc.user_id = n.user_id
        LEFT JOIN language ul ON ul.id = uc.language_id
        WHERE p.id = ? AND p.status = 'SENDING' AND p.lease_token = ? AND p.locked_until_datetime > ?
    """.trimIndent(), { rs, _ -> PushJob(rs.getLong("id"), rs.getLong("notification_id"), rs.getLong("user_id"),
        rs.getLong("user_device_id"), rs.getLong("binding_version"), rs.getString("source_key"),
        NotificationType.valueOf(rs.getString("type")), rs.getString("push_token"), rs.getString("language"),
        rs.getString("provider_ticket_id"), rs.getInt("attempt_count"), instant(rs, "created_datetime")!!,
        rs.getBoolean("valid_binding")) }, utc(now), id, lease, utc(now)).firstOrNull()

    fun finish(id: Long, lease: String, status: String, next: Instant, error: String? = null, ticket: String? = null): Boolean =
        jdbc.update("""
            UPDATE notification_push SET status = ?, next_attempt_datetime = ?, error_code = ?,
                provider_ticket_id = COALESCE(?, provider_ticket_id), locked_until_datetime = NULL, lease_token = NULL
            WHERE id = ? AND status = 'SENDING' AND lease_token = ? AND locked_until_datetime > UTC_TIMESTAMP()
        """.trimIndent(), status, utc(next), error, ticket, id, lease) == 1
}
