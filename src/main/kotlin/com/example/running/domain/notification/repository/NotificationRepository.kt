package com.example.running.domain.notification.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class NotificationType { LEAGUE_RESULT, RUNNING_REMINDER, NOTICE }

data class NotificationText(val language: String, val title: String, val body: String)
data class NotificationConfiguration(
    val pushEnabled: Boolean = false,
    val leaguePushEnabled: Boolean = true,
    val reminderPushEnabled: Boolean = true,
    val noticePushEnabled: Boolean = true,
    val timezone: String? = null,
) {
    fun allows(type: NotificationType): Boolean = pushEnabled && when (type) {
        NotificationType.LEAGUE_RESULT -> leaguePushEnabled
        NotificationType.RUNNING_REMINDER -> reminderPushEnabled
        NotificationType.NOTICE -> noticePushEnabled
    }
}

data class ReminderState(val userId: Long, val anchor: Instant, val next: Instant)
data class DeviceBinding(val id: Long, val version: Long)
data class InboxNotification(
    val id: Long, val type: NotificationType, val title: String, val body: String,
    val leagueParticipantId: Long?, val readDatetime: Instant?, val createdDatetime: Instant,
)

/** SQL lives here so queue claims and reminder advances use one atomic database statement. */
@Repository
class NotificationRepository(private val jdbc: JdbcTemplate) {
    fun languageId(code: String): Int? = jdbc.query(
        "SELECT id FROM language WHERE code = ? AND is_enabled = 1", { rs, _ -> rs.getInt(1) }, code
    ).firstOrNull()

    fun enabledLanguages(): List<String> = jdbc.query(
        "SELECT code FROM language WHERE is_enabled = 1 ORDER BY id", { rs, _ -> rs.getString(1) }
    )

    fun isActive(userId: Long): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user WHERE id = ? AND is_enabled = 1 AND is_deleted = 0", Int::class.java, userId
    ) == 1

    fun isAdmin(userId: Long): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user WHERE id = ? AND is_enabled = 1 AND is_deleted = 0 AND authority_type = 'A'",
        Int::class.java, userId
    ) == 1

    fun configuration(userId: Long): NotificationConfiguration = jdbc.query(
        "SELECT * FROM user_notification_configuration WHERE user_id = ?",
        { rs, _ -> NotificationConfiguration(rs.getBoolean("push_enabled"), rs.getBoolean("league_push_enabled"),
            rs.getBoolean("reminder_push_enabled"), rs.getBoolean("notice_push_enabled"), rs.getString("timezone")) }, userId
    ).firstOrNull() ?: NotificationConfiguration()

    fun saveConfiguration(userId: Long, value: NotificationConfiguration) {
        jdbc.update("""
            INSERT INTO user_notification_configuration
                (user_id, push_enabled, league_push_enabled, reminder_push_enabled, notice_push_enabled, timezone)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE push_enabled = VALUES(push_enabled), league_push_enabled = VALUES(league_push_enabled),
                reminder_push_enabled = VALUES(reminder_push_enabled), notice_push_enabled = VALUES(notice_push_enabled),
                timezone = VALUES(timezone)
        """.trimIndent(), userId, value.pushEnabled, value.leaguePushEnabled, value.reminderPushEnabled,
            value.noticePushEnabled, value.timezone)
    }

    fun updateTimezone(userId: Long, timezone: String) {
        jdbc.update("""
            INSERT INTO user_notification_configuration (user_id, timezone) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE timezone = VALUES(timezone)
        """.trimIndent(), userId, timezone)
    }

    fun recordCompletion(userId: Long, end: Instant) {
        // Assignment order matters: compare against the old anchor before updating it.
        jdbc.update("""
            INSERT INTO user_running_reminder_state (user_id, anchor_datetime, next_reminder_datetime)
            SELECT ?, latest_end, DATE_ADD(latest_end, INTERVAL 48 HOUR)
            FROM (
                SELECT GREATEST(?, COALESCE(MAX(end_datetime), ?)) AS latest_end
                FROM running_record WHERE user_id = ? AND is_end = 1 AND is_statistic_included = 1
            ) completed
            ON DUPLICATE KEY UPDATE
                next_reminder_datetime = IF(VALUES(anchor_datetime) > anchor_datetime,
                    VALUES(next_reminder_datetime), next_reminder_datetime),
                anchor_datetime = GREATEST(anchor_datetime, VALUES(anchor_datetime))
        """.trimIndent(), userId, utc(end), utc(end), userId)
    }

    fun dueReminders(now: Instant): List<ReminderState> = jdbc.query("""
        SELECT user_id, anchor_datetime, next_reminder_datetime FROM user_running_reminder_state
        WHERE next_reminder_datetime <= ? ORDER BY next_reminder_datetime, user_id LIMIT 100
    """.trimIndent(), { rs, _ -> ReminderState(rs.getLong(1), instant(rs, "anchor_datetime")!!,
        instant(rs, "next_reminder_datetime")!!) }, utc(now))

    fun advanceReminder(state: ReminderState, next: Instant?): Boolean = jdbc.update("""
        UPDATE user_running_reminder_state SET next_reminder_datetime = ?
        WHERE user_id = ? AND anchor_datetime = ? AND next_reminder_datetime = ?
    """.trimIndent(), next?.let(::utc), state.userId, utc(state.anchor), utc(state.next)) == 1

    fun reminderAnchor(userId: Long): Instant? = jdbc.query(
        "SELECT anchor_datetime FROM user_running_reminder_state WHERE user_id = ?",
        { rs, _ -> instant(rs, "anchor_datetime") }, userId
    ).firstOrNull()

    fun bindDevice(userId: Long, installationId: String, token: String?, platform: String, languageId: Int): DeviceBinding {
        // A token can move after reinstall. Retire the prior installation's binding first.
        if (token != null) jdbc.update("""
            UPDATE user_device SET push_token = NULL, is_enabled = 0, binding_version = binding_version + 1
            WHERE push_token = ? AND installation_id <> ?
        """.trimIndent(), token, installationId)
        jdbc.update("""
            INSERT INTO user_device (installation_id, user_id, push_token, platform, language_id, binding_version, is_enabled)
            VALUES (?, ?, ?, ?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE
                binding_version = binding_version + IF(NOT(user_id <=> VALUES(user_id))
                    OR NOT(push_token <=> VALUES(push_token)) OR is_enabled <> VALUES(is_enabled), 1, 0),
                user_id = VALUES(user_id), push_token = VALUES(push_token), platform = VALUES(platform),
                language_id = VALUES(language_id), is_enabled = VALUES(is_enabled)
        """.trimIndent(), installationId, userId, token, platform, languageId, token != null)
        return jdbc.query("SELECT id, binding_version FROM user_device WHERE installation_id = ?",
            { rs, _ -> DeviceBinding(rs.getLong(1), rs.getLong(2)) }, installationId).single()
    }

    fun unbindDevice(userId: Long, installationId: String, version: Long) {
        jdbc.update("""
            UPDATE user_device SET user_id = NULL, push_token = NULL, is_enabled = 0, binding_version = binding_version + 1
            WHERE installation_id = ? AND user_id = ? AND binding_version = ?
        """.trimIndent(), installationId, userId, version)
    }

    fun disableDevice(id: Long, version: Long) {
        jdbc.update("""
            UPDATE user_device SET is_enabled = 0, push_token = NULL, binding_version = binding_version + 1
            WHERE id = ? AND binding_version = ?
        """.trimIndent(), id, version)
    }

    fun detachAllDevices(userId: Long) {
        jdbc.update("""
            UPDATE user_device SET user_id = NULL, push_token = NULL, is_enabled = 0, binding_version = binding_version + 1
            WHERE user_id = ?
        """.trimIndent(), userId)
    }

    fun devices(userId: Long): List<DeviceBinding> = jdbc.query(
        "SELECT id, binding_version FROM user_device WHERE user_id = ? AND is_enabled = 1 AND push_token IS NOT NULL",
        { rs, _ -> DeviceBinding(rs.getLong(1), rs.getLong(2)) }, userId
    )

    fun createNotification(userId: Long, type: NotificationType, key: String, noticeId: Long?, participantId: Long?, now: Instant): Long {
        jdbc.update("""
            INSERT INTO notification (user_id, type, source_key, notice_id, league_participant_id, created_datetime, expired_datetime)
            VALUES (?, ?, ?, ?, ?, ?, DATE_ADD(?, INTERVAL 90 DAY))
            ON DUPLICATE KEY UPDATE id = id
        """.trimIndent(), userId, type.name, key, noticeId, participantId, utc(now), utc(now))
        return jdbc.queryForObject("SELECT id FROM notification WHERE user_id = ? AND source_key = ?",
            Long::class.java, userId, key)!!
    }

    fun addTranslation(id: Long, languageId: Int, text: NotificationText) {
        jdbc.update("""
            INSERT INTO notification_translation (notification_id, language_id, title, body) VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE notification_id = notification_id
        """.trimIndent(), id, languageId, text.title, text.body)
    }

    fun enqueue(id: Long, device: DeviceBinding, at: Instant) {
        jdbc.update("""
            INSERT INTO notification_push (notification_id, user_device_id, binding_version, next_attempt_datetime)
            VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE id = id
        """.trimIndent(), id, device.id, device.version, utc(at))
    }

    fun createNotice(): Long {
        val key = GeneratedKeyHolder()
        jdbc.update({ connection -> connection.prepareStatement("INSERT INTO notice () VALUES ()", Statement.RETURN_GENERATED_KEYS) }, key)
        return key.key!!.toLong()
    }

    fun addNoticeTranslation(id: Long, languageId: Int, text: NotificationText) {
        jdbc.update("INSERT INTO notice_translation (notice_id, language_id, title, body) VALUES (?, ?, ?, ?)",
            id, languageId, text.title, text.body)
    }

    fun publishNotice(id: Long, now: Instant): Boolean = jdbc.update(
        "UPDATE notice SET status = 'PUBLISHED', published_datetime = ? WHERE id = ? AND status = 'DRAFT'", utc(now), id
    ) == 1

    fun noticeStatus(id: Long): String? = jdbc.query("SELECT status FROM notice WHERE id = ?", { rs, _ -> rs.getString(1) }, id).firstOrNull()

    fun activeUserIds(after: Long): List<Long> = jdbc.query(
        "SELECT id FROM user WHERE id > ? AND is_enabled = 1 AND is_deleted = 0 ORDER BY id LIMIT 500",
        { rs, _ -> rs.getLong(1) }, after
    )

    fun inbox(userId: Long, language: String, cursor: Long?, size: Int, now: Instant, id: Long? = null, type: NotificationType? = null): List<InboxNotification> = jdbc.query("""
        SELECT n.id, n.type, n.league_participant_id, n.read_datetime, n.created_datetime,
            COALESCE(nt.title, ft.title, pt.title, pf.title) AS title,
            COALESCE(nt.body, ft.body, pt.body, pf.body) AS body
        FROM notification n
        LEFT JOIN language lang ON lang.code = ?
        JOIN language fallback ON fallback.code = 'ko'
        LEFT JOIN notification_translation nt ON nt.notification_id = n.id AND nt.language_id = lang.id
        LEFT JOIN notification_translation ft ON ft.notification_id = n.id AND ft.language_id = fallback.id
        LEFT JOIN notice_translation pt ON pt.notice_id = n.notice_id AND pt.language_id = lang.id
        LEFT JOIN notice_translation pf ON pf.notice_id = n.notice_id AND pf.language_id = fallback.id
        WHERE n.user_id = ? AND n.expired_datetime > ?
            ${if (cursor != null) "AND n.id < ?" else ""} ${if (id != null) "AND n.id = ?" else ""}
            ${if (type != null) "AND n.type = ?" else ""}
        ORDER BY n.id DESC LIMIT ?
    """.trimIndent(), { rs, _ -> InboxNotification(rs.getLong("id"), NotificationType.valueOf(rs.getString("type")),
        rs.getString("title"), rs.getString("body"), rs.getObject("league_participant_id", java.lang.Long::class.java)?.toLong(),
        instant(rs, "read_datetime"), instant(rs, "created_datetime")!!) },
        *listOfNotNull<Any>(language, userId, utc(now), cursor, id, type?.name, size).toTypedArray())

    fun markRead(userId: Long, id: Long, now: Instant) {
        jdbc.update("""
            UPDATE notification SET read_datetime = COALESCE(read_datetime, ?)
            WHERE id = ? AND user_id = ? AND expired_datetime > ?
        """.trimIndent(), utc(now), id, userId, utc(now))
    }

    fun expiredIds(now: Instant): List<Long> = jdbc.query(
        "SELECT id FROM notification WHERE expired_datetime <= ? ORDER BY expired_datetime, id LIMIT 500",
        { rs, _ -> rs.getLong(1) }, utc(now)
    )

    fun deleteExpired(id: Long) {
        jdbc.update("DELETE FROM notification_push WHERE notification_id = ?", id)
        jdbc.update("DELETE FROM notification_translation WHERE notification_id = ?", id)
        jdbc.update("DELETE FROM notification WHERE id = ?", id)
    }

    companion object {
        fun utc(value: Instant): LocalDateTime = LocalDateTime.ofInstant(value, ZoneOffset.UTC).withNano(0)
        fun instant(rs: ResultSet, column: String): Instant? =
            rs.getObject(column, LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)
    }
}
