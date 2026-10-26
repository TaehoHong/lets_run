package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.GarminRepository
import com.example.running.exception.GarminRequestException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.time.Instant

@Service
class GarminWebhookReceiver(
    private val properties: GarminProperties,
    private val repository: GarminRepository,
    private val cipher: GarminTokenCipher,
    private val mapper: ObjectMapper,
    private val transaction: TransactionTemplate,
) {
    fun receive(token: String?, body: InputStream) {
        if (!properties.enabled || !properties.webhookToken.matches(tokenPattern)) throw GarminRequestException(503)
        val candidate = token?.takeIf { it.matches(tokenPattern) } ?: throw GarminRequestException(401)
        if (!MessageDigest.isEqual(properties.webhookToken.toByteArray(Charsets.US_ASCII), candidate.toByteArray(Charsets.US_ASCII))) {
            throw GarminRequestException(401)
        }
        val receivedAt = Instant.now()
        val bytes = body.readNBytes(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw GarminRequestException(413)
        val payload = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw GarminRequestException(400)
        }
        val events = try {
            GarminWebhookPayload.split(mapper, payload)
        } catch (_: Exception) {
            throw GarminRequestException(400)
        }
        val entries = events.map { event ->
            val eventBytes = event.toByteArray(Charsets.UTF_8)
            if (eventBytes.size > MAX_BYTES) throw GarminRequestException(413)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(eventBytes)
                .joinToString("") { "%02x".format(it) }
            hash to cipher.encrypt(event, "inbox:$hash")
        }
        // The HTTP success response is built only after every event commits.
        transaction.executeWithoutResult {
            entries.forEach { (hash, encrypted) -> repository.enqueue(hash, encrypted, receivedAt) }
        }
    }

    private val tokenPattern = Regex("[A-Za-z0-9_-]{43}")
    companion object {
        const val MAX_BYTES = GarminInputLimits.MAX_BYTES
    }
}
