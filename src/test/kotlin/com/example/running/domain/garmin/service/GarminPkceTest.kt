package com.example.running.domain.garmin.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarminPkceTest {
    @Test
    fun `challenge matches the RFC 7636 S256 test vector`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            GarminPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `state and verifier use independent unpredictable URL safe values`() {
        val verifiers = List(100) { GarminPkce.verifier() }
        val states = List(100) { GarminPkce.state() }
        assertEquals(100, verifiers.toSet().size)
        assertEquals(100, states.toSet().size)
        assertTrue(verifiers.all { it.length in 43..128 && it.matches(Regex("[A-Za-z0-9_-]+")) })
        assertTrue(states.all { it.length >= 43 && it.matches(Regex("[A-Za-z0-9_-]+")) })
        assertEquals(64, GarminPkce.stateHash(states.first()).length)
    }
}
