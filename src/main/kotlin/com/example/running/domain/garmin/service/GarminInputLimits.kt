package com.example.running.domain.garmin.service

internal object GarminInputLimits {
    const val MAX_SAMPLES = 86401
    const val MAX_BYTES = 32 * 1024 * 1024
    // AES-GCM adds a 12-byte nonce and 16-byte tag; ciphertext is Base64 after the v1. prefix.
    const val MAX_ENCRYPTED_LENGTH = 3 + ((MAX_BYTES + 28 + 2) / 3) * 4
}
