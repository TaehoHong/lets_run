package com.example.running.domain.garmin.service

import com.example.running.domain.auth.service.AppleTokenRevokeService
import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import kotlin.test.assertTrue

class GarminWithdrawalTest {
    @Test
    fun `withdrawal stops Garmin imports and schedules remote consent revocation in the existing transaction`() {
        val users = mock(UserRepository::class.java)
        val garmin = mock(GarminConnectionService::class.java)
        val accounts = mock(UserAccountService::class.java)
        val tokens = mock(OAuthTokenService::class.java)
        val notifications = mock(NotificationService::class.java)
        val user = User(7)
        `when`(users.findById(7)).thenReturn(Optional.of(user))
        `when`(tokens.findAllByUserId(7)).thenReturn(emptyList())
        val service = UserService(accounts, users, tokens, mock(AppleTokenRevokeService::class.java), notifications, garmin)
        service.withdraw(7)
        assertTrue(user.isDeleted)
        verify(garmin).stopImport(7)
        verify(accounts).disableAllByUserId(7)
        verify(notifications).detachAllDevices(7)
    }
}
