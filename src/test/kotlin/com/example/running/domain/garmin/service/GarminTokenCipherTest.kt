package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class GarminTokenCipherTest {
    private fun cipher(keyByte: Byte = 1) = GarminTokenCipher(GarminProperties(
        encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { keyByte }),
    ))

    @Test
    fun `tokens round trip with a fresh nonce and no plaintext in storage`() {
        val cipher = cipher()
        val first = cipher.encrypt("private-refresh-token", "connection:7:refresh")
        val second = cipher.encrypt("private-refresh-token", "connection:7:refresh")
        assertEquals("private-refresh-token", cipher.decrypt(first, "connection:7:refresh"))
        assertNotEquals(first, second)
        assertFalse(first.contains("private-refresh-token"))
    }

    @Test
    fun `another user or token field cannot decrypt an encrypted value`() {
        val cipher = cipher()
        val value = cipher.encrypt("private-token", "connection:7:refresh")
        assertFails { cipher.decrypt(value, "connection:8:refresh") }
        assertFails { cipher.decrypt(value, "connection:7:access") }
        assertFails { cipher(2).decrypt(value, "connection:7:refresh") }
    }

    @Test
    fun `tampered ciphertext and unknown formats are rejected`() {
        val cipher = cipher()
        val value = cipher.encrypt("private-token", "connection:7:access")
        val bytes = Base64.getDecoder().decode(value.removePrefix("v1."))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertFails { cipher.decrypt("v1." + Base64.getEncoder().encodeToString(bytes), "connection:7:access") }
        assertFails { cipher.decrypt("v2.invalid", "connection:7:access") }
    }

    @Test
    fun `an absent key fails closed without preventing a disabled integration from starting`() {
        val cipher = GarminTokenCipher(GarminProperties())
        assertFails { cipher.encrypt("private-token", "connection:7:access") }
    }
}
