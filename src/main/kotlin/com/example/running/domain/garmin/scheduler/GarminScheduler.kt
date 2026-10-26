package com.example.running.domain.garmin.scheduler

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.domain.garmin.service.GarminConnectionService
import com.example.running.domain.garmin.service.GarminInboxWorker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GarminScheduler(
    private val properties: GarminProperties,
    private val repository: GarminRepository,
    private val connections: GarminConnectionService,
    private val worker: GarminInboxWorker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun drain() {
        if (!properties.enabled) return
        repeat(20) {
            if (!worker.processOne()) return
        }
    }

    @Scheduled(fixedDelay = 60000)
    fun maintain() {
        if (!properties.enabled) return
        repository.dueConnections(Instant.now()).forEach { userId ->
            try {
                connections.maintain(userId)
            } catch (error: Exception) {
                logger.warn("Garmin maintenance failed ({})", error.javaClass.simpleName)
            }
        }
    }

    @Scheduled(fixedDelay = 3600000)
    fun cleanup() {
        if (properties.enabled) repository.cleanup(Instant.now())
    }
}
