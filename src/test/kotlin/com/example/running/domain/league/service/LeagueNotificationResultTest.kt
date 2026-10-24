package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.LeagueTier
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.exception.ApiException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LeagueNotificationResultTest {
    private val repository = mock(LeagueParticipantRepository::class.java)
    private val participants = mock(LeagueParticipantService::class.java)
    private val service = LeagueService(mock(LeagueSessionService::class.java), participants,
        mock(UserLeagueInfoService::class.java), repository, mock(UserRepository::class.java),
        mock(RunningRecordRepository::class.java))
    private val session = LeagueSession(id = 1, tier = LeagueTier(LeagueTierType.BRONZE),
        startDatetime = OffsetDateTime.parse("2026-10-01T00:00:00Z"),
        endDatetime = OffsetDateTime.parse("2026-10-08T00:00:00Z"))

    @Test
    fun `another users historical result cannot be read or confirmed`() {
        val other = LeagueParticipant(id = 11, leagueSession = session, user = User(8),
            promotionStatus = PromotionStatus.MAINTAINED)
        `when`(repository.findById(11)).thenReturn(Optional.of(other))
        assertUnavailable()
        assertFalse(other.isResultChecked)
    }

    @Test
    fun `bot result cannot be read or confirmed as a user result`() {
        val bot = LeagueParticipant(id = 11, leagueSession = session, isBot = true,
            promotionStatus = PromotionStatus.MAINTAINED)
        `when`(repository.findById(11)).thenReturn(Optional.of(bot))
        assertUnavailable()
        assertFalse(bot.isResultChecked)
    }

    @Test
    fun `missing result is unavailable to read and confirm`() {
        `when`(repository.findById(11)).thenReturn(Optional.empty())
        assertUnavailable()
    }

    @Test
    fun `unsettled own participation cannot be read or confirmed as a result`() {
        val pending = LeagueParticipant(id = 11, leagueSession = session, user = User(7))
        `when`(repository.findById(11)).thenReturn(Optional.of(pending))
        assertUnavailable()
        assertFalse(pending.isResultChecked)
    }

    @Test
    fun `opening and confirming historical result affects only the requested participation`() {
        val historical = LeagueParticipant(id = 11, leagueSession = session, user = User(7),
            finalRank = 3, totalDistance = 12345, promotionStatus = PromotionStatus.MAINTAINED)
        val latest = LeagueParticipant(id = 12, leagueSession = session, user = User(7),
            promotionStatus = PromotionStatus.PROMOTED)
        `when`(repository.findById(11)).thenReturn(Optional.of(historical))
        `when`(repository.findUncheckedResultByUserId(7)).thenReturn(latest)
        `when`(participants.countParticipants(1)).thenReturn(10)
        `when`(participants.getRankedParticipants(1, 7)).thenReturn(emptyList())
        val result = service.getResult(7, 11)
        assertEquals(3, result.finalRank)
        assertEquals(12345L, result.totalDistance)
        assertEquals(PromotionStatus.MAINTAINED, result.resultStatus)
        assertFalse(historical.isResultChecked)
        service.confirmResultById(7, 11)
        assertTrue(historical.isResultChecked)
        assertFalse(latest.isResultChecked)
    }

    private fun assertUnavailable() {
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ApiException> { service.getResult(7, 11) }.status)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ApiException> { service.confirmResultById(7, 11) }.status)
        verifyNoInteractions(participants)
    }
}
