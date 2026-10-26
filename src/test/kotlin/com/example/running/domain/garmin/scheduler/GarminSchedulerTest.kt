package com.example.running.domain.garmin.scheduler

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.domain.garmin.service.GarminConnectionService
import com.example.running.domain.garmin.service.GarminInboxWorker
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GarminSchedulerTest {
    private val repository = mock(GarminRepository::class.java)
    private val connections = mock(GarminConnectionService::class.java)
    private val worker = mock(GarminInboxWorker::class.java)

    @Test
    fun `disabled integration does not run any scheduled work`() {
        val scheduler = GarminScheduler(GarminProperties(), repository, connections, worker)

        scheduler.drain()
        scheduler.maintain()
        scheduler.cleanup()

        verifyNoInteractions(repository, connections, worker)
    }

    @Test
    fun `drain stops when no eligible event remains`() {
        `when`(worker.processOne()).thenReturn(true, false)

        enabledScheduler().drain()

        verify(worker, times(2)).processOne()
    }

    @Test
    fun `drain processes at most twenty events per scheduled run`() {
        `when`(worker.processOne()).thenReturn(true)

        enabledScheduler().drain()

        verify(worker, times(20)).processOne()
    }

    private fun enabledScheduler() = GarminScheduler(
        GarminProperties(enabled = true, clientId = "test", clientSecret = "test"),
        repository,
        connections,
        worker,
    )
}
