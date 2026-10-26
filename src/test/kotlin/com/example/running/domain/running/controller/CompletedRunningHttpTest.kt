package com.example.running.domain.running.controller

import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.running.service.CompletedRunningService
import com.example.running.domain.running.controller.dto.CompletedRunningResponse
import com.example.running.exception.ControllerAdvice
import com.example.running.security.SecurityConfig
import com.example.running.security.filter.AuthenticationFilter
import com.example.running.security.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.Filter
import org.junit.jupiter.api.*
import org.mockito.Mockito.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

class CompletedRunningHttpTest {
    @Configuration @EnableWebMvc
    class TestConfiguration {
        @Bean fun mapper(): ObjectMapper = jacksonObjectMapper()
        @Bean fun service(): CompletedRunningService = mock(CompletedRunningService::class.java)
        @Bean fun tokens(): TokenService = mock(TokenService::class.java)
    }
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var service: CompletedRunningService
    private val receivedAt = java.time.Instant.parse("2026-10-26T00:00:00Z")
    private val body = """{"schemaVersion":1,"sessionId":"b1fbb3c0-80d3-4d0d-b24d-2338e64aabf3","ownerUserId":7,"source":"WEAR_OS","startTimestamp":1000,"endTimestamp":1600,"distance":1500,"durationSec":550,"cadence":0,"heartRate":0,"calorie":0,"gpsPoints":[]}"""
    @BeforeEach fun setup() {
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this,
                "token.expiration.access=3600", "token.expiration.refresh=7200")
            register(TestConfiguration::class.java, SecurityConfig::class.java, AuthenticationFilter::class.java,
                JwtAuthenticationEntryPoint::class.java, CustomAccessDeniedHandler::class.java,
                CompletedRunningController::class.java, ControllerAdvice::class.java)
            refresh()
        }
        service = context.getBean(CompletedRunningService::class.java)
        val tokens = context.getBean(TokenService::class.java)
        `when`(tokens.verify("Bearer user")).thenReturn(true)
        `when`(tokens.getId("user")).thenReturn("7")
        `when`(tokens.getEmail("user")).thenReturn("test")
        `when`(tokens.getAuthorityType("user")).thenReturn(AuthorityType.USER)
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", Filter::class.java)).build()
    }
    @AfterEach fun cleanup() { SecurityContextHolder.clearContext(); context.close() }
    @Test fun `unauthenticated request never reaches completed service`() {
        mvc.perform(post("/api/v1/running/completed-records").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }
    @Test fun `valid JSON uses authenticated account and returns durable receipt`() {
        val request = jacksonObjectMapper().readValue(body, com.example.running.domain.running.controller.dto.CompletedRunningRequest::class.java)
        `when`(service.complete(7, request, receivedAt)).thenReturn(CompletedRunningResponse(request.sessionId, 42, "CREATED"))
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user").requestAttr("running.receivedAt", receivedAt)
            .contentType("application/json").content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.runningRecordId").value(42))
            .andExpect(jsonPath("$.status").value("CREATED"))
    }
    @Test fun `foreign owner and transient persistence failure retain distinct sanitized statuses`() {
        val request = jacksonObjectMapper().readValue(body, com.example.running.domain.running.controller.dto.CompletedRunningRequest::class.java)
        `when`(service.complete(7, request, receivedAt)).thenThrow(com.example.running.exception.ApiException(com.example.running.exception.ApiError.NOT_AUTHORIZED))
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user").requestAttr("running.receivedAt", receivedAt)
            .contentType("application/json").content(body)).andExpect(status().isForbidden)
        doThrow(org.springframework.dao.DataAccessResourceFailureException("private payload")).`when`(service).complete(7, request, receivedAt)
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user").requestAttr("running.receivedAt", receivedAt)
            .contentType("application/json").content(body)).andExpect(status().isInternalServerError)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private payload"))))
    }
    @Test fun `completed local handler preserves permanent validation reason without raw payload`() {
        val request = jacksonObjectMapper().readValue(body, com.example.running.domain.running.controller.dto.CompletedRunningRequest::class.java)
        `when`(service.complete(7, request, receivedAt)).thenThrow(com.example.running.exception.RunningValidationException(
            com.example.running.exception.RunningValidationReason.RUNNING_FUTURE_END_TIME))
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user")
            .requestAttr("running.receivedAt", receivedAt).contentType("application/json").content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.extra.code").value("RUNNING_FUTURE_END_TIME"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("b1fbb3c0"))))
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = [
        "{\"longitude\":127,\"timestampMs\":1000000,\"speed\":0,\"altitude\":0,\"accuracy\":null}",
        "{\"latitude\":null,\"longitude\":127,\"timestampMs\":1000000,\"speed\":0,\"altitude\":0,\"accuracy\":null}",
        "{\"latitude\":37,\"longitude\":127,\"timestampMs\":1000000.5,\"speed\":0,\"altitude\":0,\"accuracy\":null}"
    ])
    fun `missing null or fractional route fields cannot become zero`(point: String) {
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user").requestAttr("running.receivedAt", receivedAt)
            .contentType("application/json").content(body.replace("\"gpsPoints\":[]", "\"gpsPoints\":[$point]")))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
    @Test fun `malformed payload is a sanitized client error`() {
        mvc.perform(post("/api/v1/running/completed-records").header("Authorization", "Bearer user").requestAttr("running.receivedAt", receivedAt)
            .contentType("application/json").content(body.replace("\"distance\":1500", "\"distance\":null")))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("b1fbb3c0"))))
        verifyNoInteractions(service)
    }
}
