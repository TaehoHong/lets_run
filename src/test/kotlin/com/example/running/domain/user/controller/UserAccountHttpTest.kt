package com.example.running.domain.user.controller

import com.example.running.domain.auth.service.OAuthService
import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.exception.ControllerAdvice
import com.example.running.security.vo.AuthenticationVo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class UserAccountHttpTest {
    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `last login deletion returns the code consumed by existing clients without deleting tokens`() {
        val accounts = mock(UserAccountRepository::class.java)
        val users = mock(UserRepository::class.java)
        val tokens = mock(OAuthTokenService::class.java)
        val user = User(7)
        val account = UserAccount(user, email = "last@example.test", accountTypeId = AccountTypeName.GOOGLE.id, password = null)
        ReflectionTestUtils.setField(account, "id", 11L)
        `when`(users.findByIdForUpdate(7)).thenReturn(user)
        `when`(accounts.findAllByUserIdForUpdate(7)).thenReturn(listOf(account))
        val service = UserAccountService(mock(PasswordEncoder::class.java), accounts, tokens, users)
        val controller = UserAccountController(mock(UserService::class.java), service, mock(OAuthService::class.java), tokens)
        val mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(ControllerAdvice()).build()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("test-user", null).apply { details = AuthenticationVo(7) }

        mvc.perform(delete("/api/v1/users/accounts/11"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("LAST_ACCOUNT_CANNOT_DISCONNECT"))
            .andExpect(jsonPath("$.message").value("마지막 계정은 해제할 수 없습니다."))

        verifyNoInteractions(tokens)
        verify(accounts, never()).deleteById(anyLong())
    }
}
