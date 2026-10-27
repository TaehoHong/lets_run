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
    val configuration: NotificationConfiguration, val anchor: Instant?,
)
data class PushOutcome(
    val id: Long, val lease: String, val status: String, val next: Instant,
    val error: String? = null, val ticket: String? = null,
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

    fun claim(candidates: List<PushCandidate>, lease: String, now: Instant) {
        if (candidates.isEmpty()) return
        require(candidates.size <= 100)
        val rows = candidates.joinToString(" UNION ALL ") { "SELECT ? AS id, ? AS expected_status" }
        jdbc.update("""
            UPDATE notification_push p JOIN ($rows) requested ON requested.id = p.id
            SET p.status = 'SENDING', p.lease_token = ?, p.locked_until_datetime = ?
            WHERE p.status = requested.expected_status AND p.status IN ('PENDING', 'RECEIPT_PENDING')
                AND p.next_attempt_datetime <= ?
        """.trimIndent(), *(candidates.flatMap { listOf(it.id, it.status) } +
            listOf(lease, utc(now.plusSeconds(60)), utc(now))).toTypedArray<Any?>())
    }

    fun claimed(ids: List<Long>, lease: String, now: Instant): List<PushJob> {
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= 100)
        return jdbc.query("""
            SELECT p.*, n.user_id, n.type, n.source_key, d.push_token,
                COALESCE(ul.code, dl.code) AS language, r.anchor_datetime,
                c.push_enabled, c.league_push_enabled, c.reminder_push_enabled, c.notice_push_enabled,
                (d.user_id = n.user_id AND d.binding_version = p.binding_version AND d.is_enabled = 1
                    AND u.is_enabled = 1 AND u.is_deleted = 0 AND n.expired_datetime > ?) AS valid_binding
            FROM notification_push p JOIN notification n ON n.id = p.notification_id
            JOIN user_device d ON d.id = p.user_device_id JOIN user u ON u.id = n.user_id
            JOIN language dl ON dl.id = d.language_id
            LEFT JOIN user_configuration uc ON uc.user_id = n.user_id
            LEFT JOIN language ul ON ul.id = uc.language_id
            LEFT JOIN user_notification_configuration c ON c.user_id = n.user_id
            LEFT JOIN user_running_reminder_state r ON r.user_id = n.user_id
            WHERE p.id IN (${ids.joinToString { "?" }}) AND p.status = 'SENDING'
                AND p.lease_token = ? AND p.locked_until_datetime > ?
            ORDER BY p.id
        """.trimIndent(), { rs, _ -> PushJob(rs.getLong("id"), rs.getLong("notification_id"), rs.getLong("user_id"),
            rs.getLong("user_device_id"), rs.getLong("binding_version"), rs.getString("source_key"),
            NotificationType.valueOf(rs.getString("type")), rs.getString("push_token"), rs.getString("language"),
            rs.getString("provider_ticket_id"), rs.getInt("attempt_count"), instant(rs, "created_datetime")!!,
            rs.getBoolean("valid_binding"), NotificationConfiguration(rs.getBoolean("push_enabled"),
                rs.getBoolean("league_push_enabled"), rs.getBoolean("reminder_push_enabled"),
                rs.getBoolean("notice_push_enabled")), instant(rs, "anchor_datetime")) },
            *(listOf(utc(now)) + ids + listOf(lease, utc(now))).toTypedArray<Any?>())
    }

    fun beginDelivery(jobs: List<PushJob>, lease: String, deliveryLease: String): Set<Long> {
        if (jobs.isEmpty()) return emptySet()
        require(jobs.size <= 100)
        val rows = jobs.joinToString(" UNION ALL ") {
            "SELECT ? AS id, ? AS attempt_count, ? AS push_token, ? AS anchor_datetime, ? AS ticket_id"
        }
        jdbc.update("""
            UPDATE notification_push p
            JOIN ($rows) prepared ON prepared.id = p.id
            JOIN notification n ON n.id = p.notification_id
            JOIN user_device d ON d.id = p.user_device_id
            JOIN user u ON u.id = n.user_id
            LEFT JOIN user_notification_configuration c ON c.user_id = n.user_id
            LEFT JOIN user_running_reminder_state r ON r.user_id = n.user_id
            SET p.attempt_count = p.attempt_count + IF(p.provider_ticket_id IS NULL, 1, 0), p.lease_token = ?,
                p.locked_until_datetime = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 60 SECOND)
            WHERE p.status = 'SENDING' AND p.lease_token = ?
                AND p.locked_until_datetime > UTC_TIMESTAMP() AND p.attempt_count = prepared.attempt_count
                AND BINARY p.provider_ticket_id <=> BINARY prepared.ticket_id
                AND (p.provider_ticket_id IS NOT NULL OR (p.attempt_count < 5
                    AND d.user_id = n.user_id AND d.binding_version = p.binding_version
                    AND d.is_enabled = 1 AND BINARY d.push_token = BINARY prepared.push_token
                    AND u.is_enabled = 1 AND u.is_deleted = 0 AND n.expired_datetime > UTC_TIMESTAMP()
                    AND c.push_enabled = 1
                    AND CASE n.type
                        WHEN 'LEAGUE_RESULT' THEN c.league_push_enabled
                        WHEN 'RUNNING_REMINDER' THEN c.reminder_push_enabled
                        WHEN 'NOTICE' THEN c.notice_push_enabled
                        ELSE 0 END = 1
                    AND (n.type <> 'RUNNING_REMINDER' OR r.anchor_datetime = prepared.anchor_datetime)))
        """.trimIndent(), *(jobs.flatMap { listOf(it.id, it.attemptCount, it.token, it.anchor?.let(::utc), it.ticketId) } +
            listOf(deliveryLease, lease)).toTypedArray<Any?>())
        // The new token identifies only rows that passed this gate, even when other rows lost their lease.
        return jdbc.query("""
            SELECT id FROM notification_push WHERE id IN (${jobs.joinToString { "?" }})
                AND status = 'SENDING' AND lease_token = ? AND locked_until_datetime > UTC_TIMESTAMP()
        """.trimIndent(), { rs, _ -> rs.getLong(1) },
            *(jobs.map { it.id } + deliveryLease).toTypedArray<Any?>()).toSet()
    }

    fun finish(outcomes: List<PushOutcome>) {
        if (outcomes.isEmpty()) return
        require(outcomes.size <= 100)
        val rows = outcomes.joinToString(" UNION ALL ") {
            "SELECT ? AS id, ? AS lease_token, ? AS status, ? AS next_attempt, ? AS error_code, ? AS ticket_id"
        }
        // Only an owned result row can join a device for invalidation. Both updates are one atomic statement.
        jdbc.update("""
            UPDATE notification_push p JOIN ($rows) outcome ON outcome.id = p.id
            LEFT JOIN user_device d ON d.id = p.user_device_id AND d.binding_version = p.binding_version
                AND outcome.status = 'FAILED' AND outcome.error_code = 'DeviceNotRegistered'
            SET p.status = outcome.status, p.next_attempt_datetime = outcome.next_attempt,
                p.error_code = outcome.error_code, p.provider_ticket_id = COALESCE(outcome.ticket_id, p.provider_ticket_id),
                p.locked_until_datetime = NULL, p.lease_token = NULL,
                d.is_enabled = 0, d.push_token = NULL, d.binding_version = d.binding_version + 1
            WHERE p.status = 'SENDING' AND p.lease_token = outcome.lease_token
                AND p.locked_until_datetime > UTC_TIMESTAMP()
        """.trimIndent(), *outcomes.flatMap {
            listOf(it.id, it.lease, it.status, utc(it.next), it.error, it.ticket)
        }.toTypedArray<Any?>())
    }
}
