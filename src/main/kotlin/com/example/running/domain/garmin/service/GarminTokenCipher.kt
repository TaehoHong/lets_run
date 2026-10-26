package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class GarminTokenCipher(properties: GarminProperties) {
    private val random = SecureRandom()
    private val key = properties.encryptionKey.takeIf { it.isNotBlank() }?.let {
        val bytes = try {
            Base64.getDecoder().decode(it)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Garmin encryption key must be a Base64 encoded 32 byte key")
        }
        require(bytes.size == 32) { "Garmin encryption key must be a Base64 encoded 32 byte key" }
        SecretKeySpec(bytes, "AES")
    }

    init {
        require(!properties.enabled || key != null) { "Garmin encryption key is required when enabled" }
    }

    fun encrypt(value: String, context: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context)
        return "v1." + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(value: String, context: String): String {
        require(value.startsWith("v1.")) { "Invalid Garmin encrypted value" }
        val bytes = try {
            Base64.getDecoder().decode(value.substring(3))
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Garmin encrypted value")
        }
        require(bytes.size >= 28) { "Invalid Garmin encrypted value" }
        return try {
            cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, 12), context)
                .doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("Invalid Garmin encrypted value")
        }
    }

    private fun cipher(mode: Int, nonce: ByteArray, context: String): Cipher {
        require(context.isNotBlank()) { "Garmin encryption context is required" }
        val key = checkNotNull(key) { "Garmin encryption key is not configured" }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, nonce))
            updateAAD("GARMIN:$context".toByteArray(Charsets.UTF_8))
        }
    }
}
