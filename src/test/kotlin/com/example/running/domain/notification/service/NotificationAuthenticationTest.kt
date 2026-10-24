package com.example.running.domain.notification.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.running.security.filter.AuthenticationFilter
import com.example.running.security.service.TokenService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NotificationAuthenticationTest {
    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `invalid credentials cannot continue to notification or admin controllers`() {
        val tokenService = mock(TokenService::class.java)
        doThrow(IllegalArgumentException("Invalid token private-credential-marker")).`when`(tokenService).verify("Bearer test-invalid")
        val filter = AuthenticationFilter(tokenService, jacksonObjectMapper())
        val chain = mock(FilterChain::class.java)
        val request = MockHttpServletRequest("POST", "/api/v1/admin/notices").apply {
            addHeader("Authorization", "Bearer test-invalid")
        }
        val response = MockHttpServletResponse()
        val logger = LoggerFactory.getLogger(AuthenticationFilter::class.java) as Logger
        val logs = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logs)
        try {
            filter.doFilter(request, response, chain)
        } finally {
            logger.detachAppender(logs)
            logs.stop()
        }
        assertEquals(401, response.status)
        assertFalse(response.contentAsString.contains("private-credential-marker"))
        val rejection = logs.list.single { it.formattedMessage.startsWith("Token verification failed") }
        assertFalse(rejection.formattedMessage.contains("private-credential-marker"))
        assertNull(rejection.throwableProxy)
        verifyNoInteractions(chain)
        verify(tokenService, never()).getId(anyString())
    }
}
