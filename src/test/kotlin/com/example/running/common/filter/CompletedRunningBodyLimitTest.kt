package com.example.running.common.filter

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CompletedRunningBodyLimitTest {
    private fun request(body: String, knownLength: Boolean = true) = object : MockHttpServletRequest("POST", "/api/v1/running/completed-records") {
        override fun getContentLengthLong(): Long = if (knownLength) super.getContentLengthLong() else -1
        override fun getContentLength(): Int = if (knownLength) super.getContentLength() else -1
    }.apply { setContent(body.toByteArray()) }

    @Test fun `known oversized body is rejected before reading the stream`() {
        val request = object : MockHttpServletRequest("POST", "/api/v1/running/completed-records") {
            override fun getContentLengthLong() = 33L
            override fun getInputStream(): jakarta.servlet.ServletInputStream = error("oversized body must not be read")
        }
        val response = MockHttpServletResponse()
        RequestResponseWrapperFilter(32).doFilter(request, response, FilterChain { _, _ -> fail("must not reach controller") })
        assertEquals(413, response.status)
        assertFalse(response.contentAsString.contains("private"))
    }

    @Test fun `chunked oversized body is bounded and returns sanitized HTTP 413`() {
        val response = MockHttpServletResponse()
        RequestResponseWrapperFilter(32).doFilter(request("private-route".repeat(4), false), response,
            FilterChain { _, _ -> fail("must not reach controller") })
        assertEquals(413, response.status)
        assertFalse(response.contentAsString.contains("private-route"))
    }

    @Test fun `body at limit is replayable and downstream response is copied`() {
        val response = MockHttpServletResponse()
        RequestResponseWrapperFilter(32).doFilter(request("a".repeat(32), false), response, FilterChain { input, output ->
            assertEquals("a".repeat(32), input.inputStream.readBytes().toString(Charsets.UTF_8))
            assertEquals("a".repeat(32), input.inputStream.readBytes().toString(Charsets.UTF_8))
            output.writer.write("accepted")
        })
        assertEquals(200, response.status)
        assertEquals("accepted", response.contentAsString)
    }

    @Test fun `other endpoints preserve existing body behavior`() {
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("POST", "/api/v1/running/import-batch").apply { setContent("a".repeat(33).toByteArray()) }
        RequestResponseWrapperFilter(32).doFilter(request, response, FilterChain { input, _ ->
            assertEquals(33, input.inputStream.readBytes().size)
        })
        assertEquals(200, response.status)
    }
}
