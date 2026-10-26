package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.repository.*
import com.example.running.domain.garmin.service.dto.GarminTokens
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.GarminApiException
import com.example.running.exception.GarminRequestException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Base64
import java.util.Optional
import kotlin.test.*

class GarminConnectionServiceTest {
    private val repository = mock(GarminRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val configurations = mock(UserConfigurationService::class.java)
    private val api = mock(GarminApiClient::class.java)
    private val properties = GarminProperties(enabled = true, clientId = "test-client", clientSecret = "test-secret",
        encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
    private val cipher = GarminTokenCipher(properties)
    private val manager = mock(PlatformTransactionManager::class.java)
    private val service = GarminConnectionService(properties, repository, users, configurations, cipher, api, TransactionTemplate(manager))
    private val state = "s".repeat(43)
    private val hash = GarminPkce.stateHash(state)
    private val claimed = GarminOAuthState(hash, 7, cipher.encrypt("verifier", "state:$hash"), Instant.now().plusSeconds(600))

    @BeforeEach
    fun setup() {
        `when`(manager.getTransaction(any(TransactionDefinition::class.java))).thenReturn(mock(TransactionStatus::class.java))
        `when`(users.findById(7)).thenReturn(Optional.of(User(7)))
        `when`(configurations.getOrCreateForUpdate(7)).thenReturn(UserConfiguration(7))
        `when`(repository.consumeState(eq(hash) ?: hash, any(Instant::class.java) ?: Instant.EPOCH)).thenReturn(claimed)
        `when`(repository.state(hash)).thenReturn(claimed)
        `when`(api.exchangeCode("code", "verifier")).thenReturn(GarminTokens("access", "refresh", 86400, 7776000))
        `when`(api.userId("access")).thenReturn("garmin-user")
        `when`(api.permissions("access")).thenReturn(setOf("ACTIVITY_EXPORT"))
    }

    @Test
    fun `authorization stores only hashed state and encrypted verifier bound to the current user`() {
        `when`(api.authorizationUrl(anyString(), anyString())).thenReturn("https://connect.garmin.com/oauth2Confirm")
        val response = service.authorize(7)
        val captor = ArgumentCaptor.forClass(GarminOAuthState::class.java)
        verify(repository).insertState(captor.capture() ?: claimed)
        assertEquals(GarminPkce.stateHash(response.state), captor.value.hash)
        assertEquals(7, captor.value.userId)
        val verifier = cipher.decrypt(captor.value.verifier, "state:${captor.value.hash}")
        verify(api).authorizationUrl(response.state, GarminPkce.challenge(verifier))
    }

    @Test
    fun `valid callback stores encrypted credentials and redirects without provider tokens or codes`() {
        val result = service.callback(state, "code", null)
        assertEquals("runtaeho://user/activity-connection?garminResult=connected&state=$state", result)
        val captor = ArgumentCaptor.forClass(GarminConnection::class.java)
        verify(repository).saveConnection(captor.capture() ?: value())
        assertEquals("access", cipher.decrypt(checkNotNull(captor.value.accessToken), "connection:7:access"))
        assertEquals("refresh", cipher.decrypt(checkNotNull(captor.value.refreshToken), "connection:7:refresh"))
        assertEquals(GarminConnectionStatus.CONNECTED, captor.value.status)
        verify(repository).deleteStates(7)
    }

    @Test
    fun `replayed or expired state never reaches Garmin`() {
        `when`(repository.consumeState(eq(hash) ?: hash, any(Instant::class.java) ?: Instant.EPOCH)).thenReturn(null)
        assertFailsWith<GarminRequestException> { service.callback(state, "code", null) }
        verifyNoInteractions(api)
    }

    @Test
    fun `disconnect invalidates an OAuth callback already claimed`() {
        `when`(repository.state(hash)).thenReturn(null)
        assertTrue(service.callback(state, "code", null).contains("garminResult=failed"))
        verifyNoInteractions(api)
    }

    @Test
    fun `withdrawn users cannot complete authorization`() {
        `when`(users.findById(7)).thenReturn(Optional.of(User(7).apply { withdraw() }))
        assertTrue(service.callback(state, "code", null).contains("garminResult=failed"))
        verifyNoInteractions(api)
    }

    @Test
    fun `provider cancellation consumes state but never exchanges tokens`() {
        assertTrue(service.callback(state, null, "access_denied").contains("garminResult=cancelled"))
        verifyNoInteractions(api)
    }

    @Test
    fun `one Garmin identity cannot overwrite another app user connection`() {
        `when`(repository.connectionByGarminId("garmin-user")).thenReturn(value(userId = 8))
        assertTrue(service.callback(state, "code", null).contains("garminResult=failed"))
        verify(repository, never()).saveConnection(any(GarminConnection::class.java) ?: value())
    }

    @Test
    fun `refresh stores rotated tokens and disconnect revokes before wiping credentials`() {
        `when`(repository.connection(7)).thenReturn(value(status = GarminConnectionStatus.DISCONNECTING))
        `when`(api.refresh("old-refresh")).thenReturn(GarminTokens("new-access", "new-refresh", 86400, 7776000))
        service.maintain(7)
        val order = inOrder(repository, api)
        order.verify(repository).saveConnection(any(GarminConnection::class.java) ?: value())
        order.verify(api).deregister("new-access")
        order.verify(repository).clearConnection(7)
    }

    @Test
    fun `failed remote revocation retains disconnecting state for retry`() {
        `when`(repository.connection(7)).thenReturn(value(status = GarminConnectionStatus.DISCONNECTING))
        `when`(api.refresh("old-refresh")).thenThrow(GarminApiException(503))
        service.maintain(7)
        verify(repository, never()).clearConnection(anyLong())
    }

    @Test
    fun `a revocation failure cannot roll back a refresh token already rotated by Garmin`() {
        `when`(repository.connection(7)).thenReturn(value(status = GarminConnectionStatus.DISCONNECTING))
        `when`(api.refresh("old-refresh")).thenReturn(GarminTokens("new-access", "new-refresh", 86400, 7776000))
        doThrow(GarminApiException(503)).`when`(api).deregister("new-access")
        service.maintain(7)
        val capture = ArgumentCaptor.forClass(GarminConnection::class.java)
        verify(repository).saveConnection(capture.capture() ?: value())
        assertEquals("new-refresh", cipher.decrypt(capture.value.refreshToken!!, "connection:7:refresh"))
        verify(manager).commit(any(TransactionStatus::class.java))
        verify(manager, never()).rollback(any(TransactionStatus::class.java))
        verify(repository, never()).clearConnection(7)
    }

    @Test
    fun `unknown refresh expiry still attempts refresh and preserves unknown expiry`() {
        val existing = value()
        `when`(repository.connection(7)).thenReturn(GarminConnection(existing.userId, existing.externalUserId,
            existing.status, existing.accessToken, existing.refreshToken, existing.accessExpiresAt,
            null, true, existing.connectedAt, null, null, 0))
        `when`(api.refresh("old-refresh")).thenReturn(GarminTokens("new-access", "new-refresh", 86400, null))
        service.maintain(7)
        verify(api).refresh("old-refresh")
        val capture = ArgumentCaptor.forClass(GarminConnection::class.java)
        verify(repository).saveConnection(capture.capture() ?: value())
        assertNull(capture.value.refreshExpiresAt)
    }

    @Test
    fun `starting reauthorization retains existing usable credentials`() {
        `when`(repository.connection(7)).thenReturn(value())
        `when`(api.authorizationUrl(anyString(), anyString())).thenReturn("https://connect.garmin.com/oauth2Confirm")
        service.authorize(7)
        verify(repository, never()).clearConnection(7)
        verify(repository, never()).saveConnection(any(GarminConnection::class.java) ?: value())
        verify(repository, never()).setStatus(anyLong(), any(GarminConnectionStatus::class.java) ?: GarminConnectionStatus.DISCONNECTED)
    }

    @Test
    fun `invalid refresh grant finishes disconnect and allows reauthorization`() {
        val disconnecting = value(status = GarminConnectionStatus.DISCONNECTING)
        `when`(repository.connection(7)).thenReturn(disconnecting)
        `when`(api.refresh("old-refresh")).thenThrow(GarminApiException(400, credentialsInvalid = true))
        doAnswer {
            `when`(repository.connection(7)).thenReturn(null)
            null
        }.`when`(repository).clearConnection(7)
        `when`(api.authorizationUrl(anyString(), anyString())).thenReturn("https://connect.garmin.com/oauth2Confirm")

        service.maintain(7)
        service.authorize(7)

        verify(repository).clearConnection(7)
        verify(repository).insertState(any(GarminOAuthState::class.java) ?: claimed)
        verify(api, never()).deregister(anyString())
    }

    @Test
    fun `known expired refresh finishes disconnect without contacting Garmin`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            refreshExpiresAt = Instant.now().minusSeconds(60),
        ))

        service.maintain(7)

        verify(repository).clearConnection(7)
        verifyNoInteractions(api)
    }

    @Test
    fun `unexpired access revokes directly even near expiry and with expired refresh`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            accessExpiresAt = Instant.now().plusSeconds(300),
            refreshExpiresAt = Instant.now().minusSeconds(60),
        ))

        service.maintain(7)

        verify(api).deregister("old-access")
        verify(api, never()).refresh(anyString())
        verify(repository).clearConnection(7)
    }

    @Test
    fun `rejected unexpired access refreshes and retries revocation`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            accessExpiresAt = Instant.now().plusSeconds(300),
        ))
        doThrow(GarminApiException(401)).`when`(api).deregister("old-access")
        `when`(api.refresh("old-refresh")).thenReturn(GarminTokens("new-access", "new-refresh", 86400, null))

        service.maintain(7)

        val order = inOrder(api, repository)
        order.verify(api).deregister("old-access")
        order.verify(api).refresh("old-refresh")
        order.verify(repository).saveConnection(any(GarminConnection::class.java) ?: value())
        order.verify(api).deregister("new-access")
        order.verify(repository).clearConnection(7)
    }

    @Test
    fun `missing refresh with no access finishes disconnect`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            accessToken = null,
            refreshToken = null,
        ))

        service.maintain(7)

        verify(repository).clearConnection(7)
        verifyNoInteractions(api)
    }

    @Test
    fun `missing access still uses the available refresh token`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            accessToken = null,
        ))
        `when`(api.refresh("old-refresh")).thenReturn(GarminTokens("new-access", "new-refresh", 86400, null))

        service.maintain(7)

        verify(api).refresh("old-refresh")
        verify(api).deregister("new-access")
        verify(repository).clearConnection(7)
    }

    @ParameterizedTest
    @CsvSource(
        "DISCONNECTING, 400",
        "DISCONNECTING, 401",
        "DISCONNECTING, 403",
        "DISCONNECTING, 429",
        "DISCONNECTING, 503",
        "CONNECTED, 401",
    )
    fun `unclassified refresh failures retain the current connection state`(status: GarminConnectionStatus, code: Int) {
        `when`(repository.connection(7)).thenReturn(value(status = status))
        `when`(api.refresh("old-refresh")).thenThrow(GarminApiException(code))

        service.maintain(7)

        verify(repository, never()).clearConnection(anyLong())
        verify(repository, never()).setStatus(anyLong(), any(GarminConnectionStatus::class.java) ?: GarminConnectionStatus.DISCONNECTED)
        verify(repository, never()).saveConnection(any(GarminConnection::class.java) ?: value())
    }

    @Test
    fun `unclassified revocation failure retains a usable access token for retry`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
            accessExpiresAt = Instant.now().plusSeconds(300),
        ))
        doThrow(GarminApiException(403)).`when`(api).deregister("old-access")

        service.maintain(7)

        verify(api, never()).refresh(anyString())
        verify(repository, never()).clearConnection(7)
        verify(repository, never()).setStatus(anyLong(), any(GarminConnectionStatus::class.java) ?: GarminConnectionStatus.DISCONNECTED)
    }

    @Test
    fun `invalid refresh grant on a connected account requires reconnect without clearing it`() {
        `when`(repository.connection(7)).thenReturn(value())
        `when`(api.refresh("old-refresh")).thenThrow(GarminApiException(400, credentialsInvalid = true))

        service.maintain(7)

        verify(repository).setStatus(7, GarminConnectionStatus.RECONNECT_REQUIRED)
        verify(repository, never()).clearConnection(7)
    }

    @Test
    fun `queued maintenance reads a fresh connected account only after acquiring the lock`() {
        `when`(repository.connection(7)).thenReturn(value(
            status = GarminConnectionStatus.DISCONNECTING,
        ))
        `when`(configurations.getOrCreateForUpdate(7)).thenAnswer {
            `when`(repository.connection(7)).thenReturn(value(accessExpiresAt = Instant.now().plusSeconds(86400)))
            UserConfiguration(7)
        }

        service.maintain(7)

        val order = inOrder(configurations, repository)
        order.verify(configurations).getOrCreateForUpdate(7)
        order.verify(repository).connection(7)
        verifyNoInteractions(api)
        verify(repository, never()).clearConnection(7)
    }

    private fun value(
        userId: Long = 7,
        status: GarminConnectionStatus = GarminConnectionStatus.CONNECTED,
        accessToken: String? = "old-access",
        refreshToken: String? = "old-refresh",
        accessExpiresAt: Instant? = Instant.now().minusSeconds(1),
        refreshExpiresAt: Instant? = Instant.now().plusSeconds(86400),
    ) = GarminConnection(
        userId, "garmin-user", status,
        accessToken?.let { cipher.encrypt(it, "connection:$userId:access") },
        refreshToken?.let { cipher.encrypt(it, "connection:$userId:refresh") },
        accessExpiresAt, refreshExpiresAt, true, Instant.now().minusSeconds(3600), null, null, 0,
    )
}
