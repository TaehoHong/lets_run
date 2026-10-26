package com.example.running.domain.garmin.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.running.common.interceptor.ApiLoggingInterceptor
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.util.ContentCachingResponseWrapper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GarminLoggingTest {
    @Test
    fun `Garmin request logging hides callback query headers and health payloads`() {
        val logger = LoggerFactory.getLogger(ApiLoggingInterceptor::class.java) as Logger
        val events = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(events)
        try {
            val request = MockHttpServletRequest("GET", "/api/v1/garmin/callback").apply {
                queryString = "code=private-code&state=private-state"
                addHeader("Authorization", "private-access")
                addHeader("RefreshToken", "private-refresh")
                setContent("private-gps-data".toByteArray())
            }
            val response = ContentCachingResponseWrapper(MockHttpServletResponse())
            response.writer.write("private-response")
            response.writer.flush()
            val interceptor = ApiLoggingInterceptor()
            interceptor.preHandle(request, response, Any())
            interceptor.afterCompletion(request, response, Any(), null)
            val text = events.list.joinToString { it.formattedMessage }
            assertTrue(text.contains("/api/v1/garmin/callback"))
            assertTrue(text.contains("[hidden]"))
            assertFalse(text.contains("private-"))
        } finally { logger.detachAppender(events); events.stop() }
    }
}
