package com.example.running.domain.notification.service

import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.notification.controller.*
import com.example.running.exception.ControllerAdvice
import com.example.running.security.SecurityConfig
import com.example.running.security.filter.AuthenticationFilter
import com.example.running.security.service.CustomAccessDeniedHandler
import com.example.running.security.service.JwtAuthenticationEntryPoint
import com.example.running.security.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.Filter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.context.support.TestPropertySourceUtils
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import kotlin.test.assertFalse

class NotificationHttpSecurityTest {
    @Configuration
    @EnableWebMvc
    class TestConfiguration {
        @Bean fun mapper(): ObjectMapper = jacksonObjectMapper()
        @Bean fun notifications(): NotificationService = mock(NotificationService::class.java)
        @Bean fun tokens(): TokenService = mock(TokenService::class.java)
    }

    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var service: NotificationService

    @BeforeEach
    fun setup() {
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this,
                "token.expiration.access=3600", "token.expiration.refresh=7200")
            register(TestConfiguration::class.java, SecurityConfig::class.java, AuthenticationFilter::class.java,
                JwtAuthenticationEntryPoint::class.java, CustomAccessDeniedHandler::class.java,
                NotificationController::class.java, NoticeController::class.java, NotificationControllerAdvice::class.java,
                ControllerAdvice::class.java)
            refresh()
        }
        val tokens = context.getBean(TokenService::class.java)
        `when`(tokens.verify("Bearer user")).thenReturn(true)
        `when`(tokens.getId("user")).thenReturn("7")
        `when`(tokens.getEmail("user")).thenReturn("test")
        `when`(tokens.getAuthorityType("user")).thenReturn(AuthorityType.USER)
        service = context.getBean(NotificationService::class.java)
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", Filter::class.java))
            .build()
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `anonymous inbox access is rejected by the real security chain`() {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `a normal authenticated user cannot author notices`() {
        mvc.perform(post("/api/v1/admin/notices").header("Authorization", "Bearer user")
            .contentType(MediaType.APPLICATION_JSON).content("""{"translations":[]}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `malformed request is a sanitized 400 rather than a leaked parser exception`() {
        val response = mvc.perform(put("/api/v1/notifications/device").header("Authorization", "Bearer user")
            .contentType(MediaType.APPLICATION_JSON).content("""{"pushToken":"private-token", invalid}"""))
            .andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertFalse(response.contains("private-token"))
    }

    @Test
    fun `database errors never return a device token in the response`() {
        val installation = "12345678-1234-1234-1234-123456789abc"
        doThrow(DuplicateKeyException("Duplicate ExpoPushToken[private]"))
            .`when`(service).bindDevice(7, installation, "ExpoPushToken[private]", "IOS", "ko")
        val response = mvc.perform(put("/api/v1/notifications/device").header("Authorization", "Bearer user")
            .contentType(MediaType.APPLICATION_JSON).content("""{"installationId":"$installation","pushToken":"ExpoPushToken[private]","platform":"IOS","language":"ko"}"""))
            .andExpect(status().isServiceUnavailable).andReturn().response.contentAsString
        assertFalse(response.contains("private"))
    }
}
