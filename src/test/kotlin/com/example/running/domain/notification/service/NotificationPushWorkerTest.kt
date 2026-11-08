package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.Instant
import kotlin.test.*

class NotificationPushWorkerTest {
    private fun anyInstant(): Instant = any(Instant::class.java) ?: Instant.EPOCH
    private val queue = mock(NotificationPushRepository::class.java)
    private val repository = mock(NotificationRepository::class.java)
    private val provider = mock(ExpoPushClient::class.java)
    private val worker = NotificationPushWorker(queue, repository, provider)
    private val candidates = listOf(PushCandidate(1, "PENDING"))
    private val daytime = Instant.parse("2026-10-13T12:00:00Z")
    private val job = PushJob(1, 2, 7, 3, 4, "RUNNING_REMINDER:0:48", NotificationType.RUNNING_REMINDER,
        "ExpoPushToken[test]", "ko", null, 0, Instant.now(), true,
        NotificationConfiguration(pushEnabled = true, timezone = "UTC"), Instant.EPOCH)
    private var finished = emptyList<PushOutcome>()

    @BeforeEach
    fun setup() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job))
        `when`(queue.beginDelivery(anyList(), anyString(), anyString())).thenReturn(setOf(1L))
        doAnswer { call -> finished = call.getArgument(0); null }.`when`(queue).finish(anyList())
    }

    @Test
    fun `claim loser never contacts provider`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(emptyList())
        worker.process(candidates)
        verifyNoInteractions(provider, repository)
    }

    @Test
    fun `new completed run cancels old queued reminder`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(anchor = Instant.now())))
        worker.process(candidates)
        verifyNoInteractions(provider)
        assertEquals("CANCELLED", finished.single().status)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
    }

    @Test
    fun `rebound device cancels queued push without using a send attempt`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(validBinding = false, type = NotificationType.NOTICE)))
        worker.process(candidates)
        verifyNoInteractions(provider)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
    }

    @Test
    fun `receipt acceptance is recorded without resending`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(ticketId = "ticket")))
        `when`(provider.receipts(listOf("ticket"))).thenReturn(mapOf("ticket" to PushProviderResult()))
        worker.process(candidates)
        assertEquals("PROVIDER_ACCEPTED", finished.single().status)
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `a new ticket stores the response time rather than the old queue creation time`() {
        noticeJob()
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(ticket = "new-ticket")))
        val before = Instant.now()

        worker.process(candidates)

        assertEquals("RECEIPT_PENDING", finished.single().status)
        assertEquals("new-ticket", finished.single().ticket)
        val ticketCreated = assertNotNull(finished.single().ticketCreated)
        assertTrue(ticketCreated >= before)
        assertTrue(ticketCreated <= Instant.now())
    }

    @Test
    fun `a recently issued ticket from an old queue keeps waiting for its receipt`() {
        val now = Instant.now()
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(
            ticketId = "recent-ticket", created = now.minusSeconds(7 * 86400), ticketCreated = now.minusSeconds(900))))
        `when`(provider.receipts(listOf("recent-ticket")))
            .thenReturn(mapOf("recent-ticket" to PushProviderResult(pending = true)))

        worker.process(candidates)

        assertEquals("RECEIPT_PENDING", finished.single().status)
        assertFalse(finished.single().clearTicket)
        verify(provider, never()).send(anyList())
    }

    @ParameterizedTest
    @ValueSource(strings = ["PROVIDER_UNAVAILABLE", "PROVIDER_RATE_LIMITED", "INTERRUPTED"])
    fun `an old queue with a recent ticket retains receipt transport retries`(error: String) {
        val now = Instant.now()
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(
            ticketId = "recent-ticket", created = now.minusSeconds(7 * 86400), ticketCreated = now.minusSeconds(900))))
        `when`(provider.receipts(listOf("recent-ticket")))
            .thenReturn(mapOf("recent-ticket" to PushProviderResult(error = error)))

        worker.process(candidates)

        assertEquals("RECEIPT_PENDING", finished.single().status)
        assertEquals(error, finished.single().error)
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `a receipt that remains unavailable after its ticket window is terminal`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(
            ticketId = "expired-ticket", ticketCreated = Instant.EPOCH)))
        `when`(provider.receipts(listOf("expired-ticket")))
            .thenReturn(mapOf("expired-ticket" to PushProviderResult(pending = true)))

        worker.process(candidates)

        assertEquals("FAILED", finished.single().status)
        assertEquals("RECEIPT_UNAVAILABLE", finished.single().error)
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `receipt rate limit schedules a new send with exponential backoff`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(ticketId = "ticket", attemptCount = 2)))
        `when`(provider.receipts(listOf("ticket")))
            .thenReturn(mapOf("ticket" to PushProviderResult(error = "MessageRateExceeded")))
        val before = Instant.now()

        worker.process(candidates)

        assertEquals("PENDING", finished.single().status)
        assertEquals("MessageRateExceeded", finished.single().error)
        assertTrue(finished.single().clearTicket)
        assertTrue(finished.single().next >= before.plusSeconds(60))
        assertTrue(finished.single().next <= Instant.now().plusSeconds(60))
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `receipt rate limit after the fifth send is terminal`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(ticketId = "ticket", attemptCount = 5)))
        `when`(provider.receipts(listOf("ticket")))
            .thenReturn(mapOf("ticket" to PushProviderResult(error = "MessageRateExceeded")))

        worker.process(candidates)

        assertEquals("FAILED", finished.single().status)
        assertEquals("MessageRateExceeded", finished.single().error)
        assertFalse(finished.single().clearTicket)
        verify(provider, never()).send(anyList())
    }

    @ParameterizedTest
    @ValueSource(strings = ["PROVIDER_UNAVAILABLE", "PROVIDER_RATE_LIMITED", "INTERRUPTED"])
    fun `receipt transport failure keeps polling the same ticket without resending`(error: String) {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(ticketId = "ticket", attemptCount = 2, ticketCreated = Instant.now())))
        `when`(provider.receipts(listOf("ticket")))
            .thenReturn(mapOf("ticket" to PushProviderResult(error = error)))

        worker.process(candidates)

        assertEquals("RECEIPT_PENDING", finished.single().status)
        assertFalse(finished.single().clearTicket)
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `lost lease before send never contacts provider`() {
        noticeJob()
        `when`(queue.beginDelivery(anyList(), anyString(), anyString())).thenReturn(emptySet())
        worker.process(candidates, daytime)
        verifyNoInteractions(provider)
    }

    @ParameterizedTest
    @ValueSource(strings = ["PROVIDER_UNAVAILABLE", "PROVIDER_RATE_LIMITED"])
    fun `transient provider failure retries before fifth actual send`(error: String) {
        noticeJob(attempts = 3)
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = error)))
        val before = Instant.now()
        worker.process(candidates, daytime)
        verify(provider).send(listOf(PushMessage("ExpoPushToken[test]", "Notice", "Body", 2L, 7L)))
        assertEquals("PENDING", finished.single().status)
        assertEquals(error, finished.single().error)
        assertTrue(finished.single().next >= before.plusSeconds(240))
        assertTrue(finished.single().next <= Instant.now().plusSeconds(240))
    }

    @Test
    fun `fifth actual send failure is terminal`() {
        noticeJob(attempts = 4)
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = "PROVIDER_UNAVAILABLE")))
        worker.process(candidates, daytime)
        verify(provider).send(listOf(PushMessage("ExpoPushToken[test]", "Notice", "Body", 2L, 7L)))
        assertEquals("FAILED", finished.single().status)
        assertEquals("PROVIDER_UNAVAILABLE", finished.single().error)
    }

    @Test
    fun `exhausted attempts never cause a sixth send`() {
        noticeJob(attempts = 5)
        worker.process(candidates, daytime)
        verifyNoInteractions(provider)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
        assertEquals("FAILED", finished.single().status)
        assertEquals("ATTEMPTS_EXHAUSTED", finished.single().error)
    }

    @Test
    fun `unregistered response is delegated to atomic queue finish`() {
        noticeJob()
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = "DeviceNotRegistered")))
        worker.process(candidates, daytime)
        assertEquals("FAILED", finished.single().status)
        assertEquals("DeviceNotRegistered", finished.single().error)
        verify(repository, never()).disableDevice(anyLong(), anyLong())
    }

    private fun noticeJob(attempts: Int = 0) {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(type = NotificationType.NOTICE, attemptCount = attempts)))
        `when`(repository.pushContent(anyList(), anyInstant()))
            .thenReturn(mapOf(1L to NotificationText("ko", "Notice", "Body")))
    }
}
