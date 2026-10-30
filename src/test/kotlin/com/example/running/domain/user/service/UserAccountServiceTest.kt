package com.example.running.domain.user.service

import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.AccountType
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.domain.user.repository.UserRepository
import com.example.running.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.*

class UserAccountServiceTest {
    private val repository = mock(UserAccountRepository::class.java)
    private val tokens = mock(OAuthTokenService::class.java)
    private val users = mock(UserRepository::class.java)
    private val service = UserAccountService(mock(PasswordEncoder::class.java), repository, tokens, users)
    private val accounts = linkedMapOf<Long, UserAccount>()

    init {
        `when`(users.findByIdForUpdate(7)).thenReturn(User(7))
        `when`(repository.findAllByUserIdForUpdate(anyLong())).thenAnswer { invocation ->
            accounts.values.filter { it.user.id == invocation.getArgument<Long>(0) }
        }
        doAnswer { accounts.remove(it.getArgument<Long>(0)); null }.`when`(repository).deleteById(anyLong())
    }

    @Test
    fun `last active account cannot be deleted and its OAuth token remains`() {
        account(1)

        val error = assertFailsWith<ApiException> { service.hardDelete(1, 7) }

        assertEquals("LAST_ACCOUNT_CANNOT_DISCONNECT", error.code)
        assertEquals(setOf(1L), accounts.keys)
        verifyNoInteractions(tokens)
    }

    @ParameterizedTest
    @CsvSource("false,false", "true,true")
    fun `disabled or deleted alternatives do not make the last login removable`(enabled: Boolean, deleted: Boolean) {
        account(1)
        account(2, enabled = enabled, deleted = deleted)

        val error = assertFailsWith<ApiException> { service.hardDelete(1, 7) }

        assertEquals("LAST_ACCOUNT_CANNOT_DISCONNECT", error.code)
        assertEquals(setOf(1L, 2L), accounts.keys)
        verifyNoInteractions(tokens)
    }

    @Test
    fun `two clients using the same old account list cannot sequentially remove both login methods`() {
        account(1)
        account(2)

        service.hardDelete(1, 7)
        val error = assertFailsWith<ApiException> { service.hardDelete(2, 7) }

        assertEquals("LAST_ACCOUNT_CANNOT_DISCONNECT", error.code)
        assertEquals(setOf(2L), accounts.keys)
        verify(tokens).deleteByUserAccountId(1)
        verify(tokens, never()).deleteByUserAccountId(2)
    }

    @Test
    fun `another users account cannot be deleted`() {
        account(1, ownerId = 8)
        account(2)
        account(3)

        assertFailsWith<RuntimeException> { service.hardDelete(1, 7) }

        assertEquals(setOf(1L, 2L, 3L), accounts.keys)
        verifyNoInteractions(tokens)
    }

    @Test
    fun `inactive account can be removed while preserving the only active login`() {
        account(1, enabled = false)
        account(2)

        service.hardDelete(1, 7)

        assertEquals(setOf(2L), accounts.keys)
        verify(tokens).deleteByUserAccountId(1)
    }

    private fun account(id: Long, ownerId: Long = 7, enabled: Boolean = true, deleted: Boolean = false) {
        accounts[id] = UserAccount(User(ownerId), AccountType(AccountTypeName.GOOGLE.id),
            "account$id@example.test", isEnabled = enabled, isDeleted = deleted).also {
            ReflectionTestUtils.setField(it, "id", id)
        }
    }
}
