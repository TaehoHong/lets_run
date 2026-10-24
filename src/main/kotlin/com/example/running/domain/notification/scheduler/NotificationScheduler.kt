package com.example.running.domain.notification.scheduler

import com.example.running.domain.notification.repository.NotificationPushRepository
import com.example.running.domain.notification.repository.NotificationRepository
import com.example.running.domain.notification.service.NotificationPushWorker
import com.example.running.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant

@Configuration
class NotificationSchedulingConfiguration {
    @Bean
    fun notificationTaskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("notification-")
    }
}

@Component
@ConditionalOnProperty(name = ["notifications.jobs-enabled"], havingValue = "true", matchIfMissing = true)
class NotificationScheduler(
    private val repository: NotificationRepository,
    private val queue: NotificationPushRepository,
    private val service: NotificationService,
    private val worker: NotificationPushWorker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60000, scheduler = "notificationTaskScheduler")
    fun reminders() {
        val now = Instant.now()
        repository.dueReminders(now).forEach { state ->
            safely("reminder") { service.generateReminder(state, now) }
        }
    }

    @Scheduled(fixedDelay = 1000, scheduler = "notificationTaskScheduler")
    fun pushes() {
        val now = Instant.now()
        queue.recoverExpired(now)
        queue.candidates(now).forEach { candidate -> safely("push") { worker.process(candidate) } }
    }

    @Scheduled(fixedDelay = 60000, scheduler = "notificationTaskScheduler")
    fun purge() = service.purgeExpired(Instant.now())

    private fun safely(operation: String, action: () -> Unit) {
        try { action() } catch (error: Exception) {
            logger.error("Notification {} failed ({})", operation, error.javaClass.simpleName)
        }
    }
}
