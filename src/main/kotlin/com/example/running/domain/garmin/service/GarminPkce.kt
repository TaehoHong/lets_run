package com.example.running.domain.garmin.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

object GarminPkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun verifier(): String = encoder.encodeToString(ByteArray(64).also(random::nextBytes))
    fun state(): String = encoder.encodeToString(ByteArray(32).also(random::nextBytes))
    fun challenge(verifier: String): String = encoder.encodeToString(digest(verifier))
    fun stateHash(state: String): String = HexFormat.of().formatHex(digest(state))
    private fun digest(value: String): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.US_ASCII))
}
