package com.example.running.common.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletInputStream
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant
import kotlin.test.*

class RunningRequestTimeTest {
    @Test
    fun `server receipt is captured before reading upload and cannot be chosen by client metadata`() {
        val forged = Instant.parse("2099-01-01T00:00:00Z")
        val payload = """{"receivedAt":"$forged","records":[]}"""
        var capturedBeforeRead: Instant? = null
        val request = object : MockHttpServletRequest("POST", "/api/v1/running/completed-records") {
            override fun getInputStream(): ServletInputStream {
                capturedBeforeRead = getAttribute("running.receivedAt") as? Instant
                assertNotNull(capturedBeforeRead)
                return super.getInputStream()
            }
        }.apply {
            contentType = "application/json"
            setContent(payload.toByteArray())
            addHeader("running.receivedAt", forged.toString())
            addHeader("X-Running-Received-At", forged.toString())
            setAttribute("running.receivedAt", forged)
        }
        val before = Instant.now()
        var handled = false

        RequestResponseWrapperFilter().doFilter(request, MockHttpServletResponse(), FilterChain { incoming, _ ->
            handled = true
            val receipt = incoming.getAttribute("running.receivedAt") as Instant
            assertEquals(capturedBeforeRead, receipt)
            assertFalse(receipt.isBefore(before))
            assertFalse(receipt.isAfter(Instant.now()))
            assertNotEquals(forged, receipt)
            assertEquals(payload, incoming.inputStream.readBytes().toString(Charsets.UTF_8))
        })

        assertTrue(handled)
    }
}
