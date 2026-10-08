package com.example.running.domain.user.controller

import com.example.running.common.service.S3Service
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.UserConfigurationRepository
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.domain.user.service.UserService
import com.example.running.exception.ControllerAdvice
import com.example.running.security.vo.AuthenticationVo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class UserConfigurationLanguageTest {
    private val repository = mock(UserConfigurationRepository::class.java)
    private val configuration = UserConfiguration(userId = 7, healthImportEnabled = true)
    private val controller = UserController(
        mock(UserAccountService::class.java), mock(UserService::class.java),
        UserConfigurationService(repository), mock(S3Service::class.java)
    )
    private val mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(ControllerAdvice()).build()

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("test-user", null).apply { details = AuthenticationVo(7) }
        `when`(repository.findByUserIdForUpdate(7)).thenReturn(configuration)
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `saving Japanese preserves Health settings and subsequent Health edits preserve Japanese`() {
        update("""{"language":"ja"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("ja"))
            .andExpect(jsonPath("$.healthImportEnabled").value(true))
        update("""{"healthImportEnabled":false}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("ja"))
            .andExpect(jsonPath("$.healthImportEnabled").value(false))
    }

    @Test
    fun `new configuration follows the device language`() {
        update("{}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("system"))
    }

    @Test
    fun `unsupported language is rejected without writing settings`() {
        update("""{"language":"en"}""").andExpect(status().isBadRequest)
        verifyNoInteractions(repository)
    }

    @Test
    fun `request cannot select another user's settings`() {
        update("""{"userId":99,"language":"ko"}""").andExpect(status().isOk)
        verify(repository).findByUserIdForUpdate(7)
        verify(repository, never()).findByUserIdForUpdate(99)
    }

    @Test
    fun `reading settings on another device returns the saved language`() {
        update("""{"language":"ja"}""").andExpect(status().isOk)
        `when`(repository.findById(7)).thenReturn(Optional.of(configuration))
        mvc.perform(get("/api/v1/users/configuration"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("ja"))
            .andExpect(jsonPath("$.userId").value(7))
    }

    private fun update(body: String) = mvc.perform(
        patch("/api/v1/users/configuration").contentType(MediaType.APPLICATION_JSON).content(body)
    )
}
