package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import com.example.running.exception.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

@Service
class NotificationService(private val repository: NotificationRepository) {
    @Transactional
    fun recordCompletion(userId: Long, end: Instant) = repository.recordCompletion(userId, end)

    @Transactional
    fun generateReminder(state: ReminderState, now: Instant = Instant.now()): Boolean {
        if (state.next > now) return false
        val key = NotificationPolicy.reminderKey(state.anchor, state.next)
        if (!repository.advanceReminder(state, NotificationPolicy.followingReminderAt(state.anchor, state.next))) return false
        if (!repository.isActive(state.userId)) return true
        create(state.userId, NotificationType.RUNNING_REMINDER, key, null, null, listOf(
            NotificationText("ko", "다시 달려볼까요?", "잠시 쉬었다면, 오늘 가볍게 달려보세요."),
            NotificationText("ja", "また走ってみませんか？", "少し休んだら、今日は軽く走ってみましょう。")
        ), now)
        return true
    }

    @Transactional
    fun leagueResult(userId: Long, participantId: Long, rank: Int) {
        if (!repository.isActive(userId)) return
        create(userId, NotificationType.LEAGUE_RESULT, "LEAGUE_RESULT:$participantId", null, participantId, listOf(
            NotificationText("ko", "리그 결과가 나왔어요", "이번 리그 최종 순위는 ${rank}위예요. 결과를 확인해보세요."),
            NotificationText("ja", "リーグ結果が届きました", "今回のリーグ最終順位は${rank}位です。結果を確認しましょう。")
        ), Instant.now())
    }

    private fun create(userId: Long, type: NotificationType, key: String, noticeId: Long?, participantId: Long?,
                       texts: List<NotificationText>, now: Instant) {
        val id = repository.createNotification(userId, type, key, noticeId, participantId, now)
        texts.forEach { text -> repository.languageId(text.language)?.let { repository.addTranslation(id, it, text) } }
        val configuration = repository.configuration(userId)
        if (!configuration.allows(type)) return
        repository.devices(userId).forEach { repository.enqueue(id, it, now) }
    }

    @Transactional(readOnly = true)
    fun configuration(userId: Long): NotificationConfiguration {
        requireActive(userId)
        return repository.configuration(userId)
    }

    @Transactional
    fun configure(userId: Long, value: NotificationConfiguration): NotificationConfiguration {
        requireActive(userId)
        if (value.pushEnabled && value.timezone == null) badRequest("Timezone is required")
        value.timezone?.let {
            try { ZoneId.of(it) } catch (_: DateTimeException) { badRequest("Invalid timezone") }
        }
        repository.saveConfiguration(userId, value)
        return value
    }

    @Transactional
    fun updateTimezone(userId: Long, timezone: String) {
        requireActive(userId)
        if (timezone.isBlank() || timezone.length > 64) badRequest("Invalid timezone")
        try { ZoneId.of(timezone) } catch (_: DateTimeException) { badRequest("Invalid timezone") }
        repository.updateTimezone(userId, timezone)
    }

    @Transactional
    fun bindDevice(userId: Long, installationId: String, token: String?, platform: String, language: String): DeviceBinding {
        requireActive(userId)
        val languageId = repository.languageId(language) ?: badRequest("Unsupported language")
        return repository.bindDevice(userId, installationId, token, platform, languageId)
    }

    @Transactional
    fun unbindDevice(userId: Long, installationId: String, version: Long) = repository.unbindDevice(userId, installationId, version)

    @Transactional
    fun detachAllDevices(userId: Long) = repository.detachAllDevices(userId)

    @Transactional(readOnly = true)
    fun inbox(userId: Long, language: String, cursor: Long?, size: Int, type: NotificationType? = null): List<InboxNotification> {
        requireActive(userId)
        if (size !in 1..100 || (cursor != null && cursor <= 0)) badRequest("Invalid pagination")
        return repository.inbox(userId, language, cursor, size + 1, Instant.now(), type = type)
    }

    @Transactional
    fun open(userId: Long, id: Long, language: String): InboxNotification {
        requireActive(userId)
        val now = Instant.now()
        val item = repository.inbox(userId, language, null, 1, now, id).firstOrNull()
            ?: throw ApiException("Notification not found", HttpStatus.NOT_FOUND)
        repository.markRead(userId, id, now)
        return item.copy(readDatetime = item.readDatetime ?: now)
    }

    @Transactional
    fun createNotice(adminId: Long, texts: List<NotificationText>): Long {
        requireAdmin(adminId)
        val codes = texts.map { it.language }
        if (texts.isEmpty() || codes.toSet().size != codes.size || !codes.containsAll(repository.enabledLanguages())) {
            badRequest("Provide one translation for each enabled language")
        }
        val translations = texts.map { text ->
            if (text.title.isBlank() || text.title.length > 255 || text.body.isBlank() || text.body.toByteArray().size > 65535) {
                badRequest("Invalid notice text")
            }
            (repository.languageId(text.language) ?: badRequest("Unsupported language")) to text
        }
        val id = repository.createNotice()
        translations.forEach { (languageId, text) -> repository.addNoticeTranslation(id, languageId, text) }
        return id
    }

    @Transactional
    fun publishNotice(adminId: Long, id: Long) {
        requireAdmin(adminId)
        val now = Instant.now()
        if (!repository.publishNotice(id, now)) {
            if (repository.noticeStatus(id) == null) throw ApiException("Notice not found", HttpStatus.NOT_FOUND)
            return
        }
        // One transaction and repeatable-read snapshot: publication and recipient creation are all-or-nothing.
        var cursor = 0L
        while (true) {
            val users = repository.activeUserIds(cursor)
            if (users.isEmpty()) break
            users.forEach { create(it, NotificationType.NOTICE, "NOTICE:$id", id, null, emptyList(), now) }
            cursor = users.last()
        }
    }

    @Transactional
    fun purgeExpired(now: Instant) = repository.expiredIds(now).forEach(repository::deleteExpired)

    private fun requireActive(userId: Long) {
        if (!repository.isActive(userId)) throw ApiException("Account unavailable", HttpStatus.FORBIDDEN)
    }

    private fun requireAdmin(userId: Long) {
        if (!repository.isAdmin(userId)) throw ApiException("Administrator required", HttpStatus.FORBIDDEN)
    }

    private fun badRequest(message: String): Nothing = throw ApiException(message, HttpStatus.BAD_REQUEST)
}
