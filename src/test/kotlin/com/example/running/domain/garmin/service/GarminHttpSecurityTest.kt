package com.example.running.domain.garmin.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.garmin.controller.*
import com.example.running.domain.garmin.controller.dto.GarminAuthorizationResponse
import com.example.running.domain.garmin.controller.dto.GarminConnectionResponse
import com.example.running.domain.garmin.repository.GarminConnectionStatus
import com.example.running.exception.ControllerAdvice
import com.example.running.exception.GarminControllerAdvice
import com.example.running.security.SecurityConfig
import com.example.running.security.filter.AuthenticationFilter
import com.example.running.security.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.Filter
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.support.TestPropertySourceUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.time.Instant
import kotlin.test.assertFalse

class GarminHttpSecurityTest {
    @Configuration @EnableWebMvc
    class TestConfiguration {
        @Bean fun mapper(): ObjectMapper = jacksonObjectMapper()
        @Bean fun connections(): GarminConnectionService = mock(GarminConnectionService::class.java)
        @Bean fun receiver(): GarminWebhookReceiver = mock(GarminWebhookReceiver::class.java)
        @Bean fun tokens(): TokenService = mock(TokenService::class.java)
    }
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var service: GarminConnectionService
    private lateinit var tokens: TokenService

    @BeforeEach
    fun setup() {
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this,
                "token.expiration.access=3600", "token.expiration.refresh=7200")
            register(TestConfiguration::class.java, SecurityConfig::class.java, AuthenticationFilter::class.java,
                JwtAuthenticationEntryPoint::class.java, CustomAccessDeniedHandler::class.java,
                GarminController::class.java, GarminWebhookController::class.java, GarminControllerAdvice::class.java, GarminPrivacyFilter::class.java, ControllerAdvice::class.java)
            refresh()
        }
        service = context.getBean(GarminConnectionService::class.java)
        tokens = context.getBean(TokenService::class.java)
        `when`(tokens.verify("Bearer user")).thenReturn(true)
        `when`(tokens.getId("user")).thenReturn("7")
        `when`(tokens.getEmail("user")).thenReturn("test")
        `when`(tokens.getAuthorityType("user")).thenReturn(AuthorityType.USER)
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean(GarminPrivacyFilter::class.java),
                context.getBean("springSecurityFilterChain", Filter::class.java)).build()
    }

    @AfterEach
    fun cleanup() { SecurityContextHolder.clearContext(); if (::context.isInitialized) context.close() }

    @Test
    fun `webhook POST uses delivery token instead of app JWT and hides the query in framework logs`() {
        val logger = LoggerFactory.getLogger("org.springframework.security.web.FilterChainProxy") as Logger
        val original = logger.level
        val events = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(events)
        try {
            mvc.perform(post("/api/v1/garmin/webhook?token=private-webhook-token")
                .header("Authorization", "Basic provider").contentType("application/json").content("{\"activities\":[]}"))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            verify(tokens, never()).verify("Basic provider")
            assertFalse(events.list.joinToString { it.formattedMessage }.contains("private-webhook-token"))
            mvc.perform(get("/api/v1/garmin/webhook?token=private-webhook-token")).andExpect(status().isUnauthorized)
        } finally { logger.detachAppender(events); logger.level = original; events.stop() }
    }

    @Test
    fun `webhook rejects form content before binding delivery parameters or reading the body`() {
        mvc.perform(post("/api/v1/garmin/webhook?token=private-query-token")
            .contentType("application/x-www-form-urlencoded").content("token=private-body-token&activities=private-data"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(Matchers.not(Matchers.containsString("private-"))))
        verifyNoInteractions(context.getBean(GarminWebhookReceiver::class.java))
    }

    @Test
    fun `all connection management operations require app JWT authentication`() {
        mvc.perform(get("/api/v1/garmin/connection")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/garmin/authorization")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/v1/garmin/connection")).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `status uses only the authenticated user and is not browser cacheable`() {
        `when`(service.connection(7)).thenReturn(GarminConnectionResponse(false, GarminConnectionStatus.DISCONNECTED))
        mvc.perform(get("/api/v1/garmin/connection?userId=8").header("Authorization", "Bearer user"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("DISCONNECTED"))
        verify(service).connection(7)
        verify(service, never()).connection(8)
    }

    @Test
    fun `authorization forwards the app scheme for the authenticated user`() {
        val state = "runtaeho-dev_" + "a".repeat(43)
        `when`(service.authorize(7, "runtaeho-dev")).thenReturn(GarminAuthorizationResponse(
            "https://connect.garmin.com/oauth2Confirm?state=$state", state, Instant.EPOCH))

        mvc.perform(post("/api/v1/garmin/authorization").header("Authorization", "Bearer user")
            .contentType("application/json").content("{\"appScheme\":\"runtaeho-dev\"}"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.state").value(state))

        verify(service).authorize(7, "runtaeho-dev")
    }

    @Test
    fun `authorization without a body remains compatible with existing clients`() {
        val state = "a".repeat(43)
        `when`(service.authorize(7)).thenReturn(GarminAuthorizationResponse(
            "https://connect.garmin.com/oauth2Confirm?state=$state", state, Instant.EPOCH))

        mvc.perform(post("/api/v1/garmin/authorization").header("Authorization", "Bearer user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value(state))

        verify(service).authorize(7)
    }

    @Test
    fun `OAuth callback is public and does not interpret an unrelated authorization header as an app token`() {
        `when`(service.callback("test-state", "test-code", null)).thenReturn("runtaeho://user/activity-connection?garminResult=connected&state=test-state")
        mvc.perform(get("/api/v1/garmin/callback?state=test-state&code=test-code").header("Authorization", "Basic unrelated"))
            .andExpect(status().isFound).andExpect(header().string("Referrer-Policy", "no-referrer"))
        verify(tokens, never()).verify("Basic unrelated")
    }

    @Test
    fun `callback requires state and storage exceptions cannot expose query credentials`() {
        mvc.perform(get("/api/v1/garmin/callback?code=private-code")).andExpect(status().isBadRequest)
        `when`(service.connection(7)).thenThrow(DataAccessResourceFailureException("private-access-token"))
        val result = mvc.perform(get("/api/v1/garmin/connection").header("Authorization", "Bearer user"))
            .andExpect(status().isServiceUnavailable).andReturn().response.contentAsString
        assertFalse(result.contains("private"))
    }

    @Test
    fun `framework security logging cannot expose OAuth query credentials`() {
        val logger = LoggerFactory.getLogger("org.springframework.security.web.FilterChainProxy") as Logger
        val original = logger.level
        val events = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(events)
        try {
            `when`(service.callback("test-state", "private-code", null)).thenReturn("runtaeho://user/activity-connection")
            mvc.perform(get("/api/v1/garmin/callback?state=test-state&code=private-code")).andExpect(status().isFound)
            assertFalse(events.list.joinToString { it.formattedMessage }.contains("private-code"))
        } finally { logger.detachAppender(events); logger.level = original; events.stop() }
    }
}
