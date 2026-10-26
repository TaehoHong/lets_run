package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.exception.GarminRequestException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GarminWebhookReceiverTest {
    private val repository = mock(GarminRepository::class.java)
    private val transaction = mock(TransactionTemplate::class.java)
    private val token = "t".repeat(43)
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

    @Test
    fun `disabled integration rejects delivery before reading the body or touching storage`() {
        val receiver = receiver(GarminProperties())
        assertEquals(503, assertFailsWith<GarminRequestException> { receiver.receive(token, unreadableBody()) }.status)
        verifyNoInteractions(repository, transaction)
    }

    @Test
    fun `missing configured delivery token fails closed independently of OAuth configuration`() {
        val receiver = receiver(GarminProperties(enabled = true, clientId = "test", clientSecret = "test", encryptionKey = key))
        assertEquals(503, assertFailsWith<GarminRequestException> { receiver.receive(token, unreadableBody()) }.status)
        verifyNoInteractions(repository, transaction)
    }

    @Test
    fun `wrong fixed length token is rejected before reading an untrusted body`() {
        val receiver = receiver(GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
            encryptionKey = key, webhookToken = token))
        assertEquals(401, assertFailsWith<GarminRequestException> { receiver.receive("x".repeat(43), unreadableBody()) }.status)
        verifyNoInteractions(repository, transaction)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[]", "{}", "{", "{\"unknown\":[]}", "{\"activities\":{}}"])
    fun `malformed envelopes are rejected before opening a transaction`(payload: String) {
        val receiver = receiver(enabledProperties())
        assertEquals(400, assertFailsWith<GarminRequestException> {
            receiver.receive(token, payload.byteInputStream())
        }.status)
        verifyNoInteractions(repository, transaction)
    }

    @Test
    fun `oversized event batch is rejected before opening a transaction`() {
        val payload = "{\"activities\":[${List(101) { "{}" }.joinToString(",")}]}"
        assertEquals(400, assertFailsWith<GarminRequestException> {
            receiver(enabledProperties()).receive(token, payload.byteInputStream())
        }.status)
        verifyNoInteractions(repository, transaction)
    }

    @Test
    fun `normalized event exceeding stored payload limit is rejected before persistence`() {
        val envelope = """{"activities":[{"userId":"u","value":1e1,"paddingA":"","paddingB":""}]}"""
        val paddingSize = GarminWebhookReceiver.MAX_BYTES - envelope.length
        val payload = envelope.replace("\"paddingA\":\"\"", "\"paddingA\":\"${"a".repeat(paddingSize / 2)}\"")
            .replace("\"paddingB\":\"\"", "\"paddingB\":\"${"a".repeat(paddingSize - paddingSize / 2)}\"")
        assertEquals(GarminWebhookReceiver.MAX_BYTES, payload.toByteArray(Charsets.UTF_8).size)
        assertEquals(413, assertFailsWith<GarminRequestException> {
            receiver(enabledProperties()).receive(token, payload.byteInputStream())
        }.status)
        verifyNoInteractions(repository, transaction)
    }

    @Test
    fun `receiver enqueues individual encrypted events including invalid event fields`() {
        val properties = enabledProperties()
        val manager = mock(PlatformTransactionManager::class.java)
        val realTransaction = TransactionTemplate(manager)
        `when`(manager.getTransaction(realTransaction)).thenReturn(mock(TransactionStatus::class.java))
        val mapper = jacksonObjectMapper()
        val cipher = GarminTokenCipher(properties)
        val receiver = GarminWebhookReceiver(properties, repository, cipher, mapper, realTransaction)
        val payload = """{"activities":[{"userId":"valid","activityId":1},{"userId":""}],"deregistrations":[null]}"""

        receiver.receive(token, payload.byteInputStream())

        val calls = mockingDetails(repository).invocations.filter { it.method.name == "enqueue" }
        assertEquals(3, calls.size)
        val decrypted = calls.map { call ->
            val hash = call.arguments[0] as String
            val event = cipher.decrypt(call.arguments[1] as String, "inbox:$hash")
            mapper.readTree(event)
        }
        assertEquals(mapper.readTree("""{"activities":[{"userId":"valid","activityId":1}]}"""), decrypted[0])
        assertEquals(mapper.readTree("""{"activities":[{"userId":""}]}"""), decrypted[1])
        assertEquals(mapper.readTree("""{"deregistrations":[null]}"""), decrypted[2])
        assertFailsWith<IllegalArgumentException> { GarminWebhookPayload.parse(mapper, decrypted[1].toString()) }
        assertFailsWith<IllegalArgumentException> { GarminWebhookPayload.parse(mapper, decrypted[2].toString()) }
    }

    @Test
    fun `event identity ignores grouping and recursive object order but preserves body type and array order`() {
        val mapper = jacksonObjectMapper()
        val original = GarminWebhookPayload.split(mapper,
            """{"activities":[{"userId":"u","activityId":1,"details":{"b":2,"a":1},"samples":[{"b":2,"a":1},3]}]}""").single()
        val regrouped = GarminWebhookPayload.split(mapper,
            """{"deregistrations":[{"userId":"other"}],"activities":[{"samples":[{"a":1,"b":2},3],"details":{"a":1,"b":2},"activityId":1,"userId":"u"},{"userId":"other"}]}""")[1]
        assertEquals(original, regrouped)
        assertNotEquals(original, GarminWebhookPayload.split(mapper, original.replace("\"b\":2", "\"b\":4")).single())
        assertNotEquals(original, GarminWebhookPayload.split(mapper, original.replace("activities", "manuallyUpdatedActivities")).single())
        assertNotEquals(original, GarminWebhookPayload.split(mapper,
            """{"activities":[{"userId":"u","activityId":1,"details":{"b":2,"a":1},"samples":[3,{"b":2,"a":1}]}]}""").single())
    }

    private fun enabledProperties() = GarminProperties(enabled = true, clientId = "test", clientSecret = "test",
        encryptionKey = key, webhookToken = token)

    private fun receiver(properties: GarminProperties) = GarminWebhookReceiver(properties, repository,
        GarminTokenCipher(properties), jacksonObjectMapper(), transaction)

    private fun unreadableBody() = object : InputStream() {
        override fun read(): Int = error("Unauthenticated body must not be read")
    }
}
