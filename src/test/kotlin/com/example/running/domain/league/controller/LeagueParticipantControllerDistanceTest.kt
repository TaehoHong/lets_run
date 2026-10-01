package com.example.running.domain.league.controller

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.LeagueTier
import com.example.running.domain.league.entity.UserLeagueInfo
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.league.service.LeagueParticipantService
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.league.service.LeagueSessionService
import com.example.running.domain.league.service.UserLeagueInfoService
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.security.vo.AuthenticationVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.OffsetDateTime
import java.util.Optional

class LeagueParticipantControllerDistanceTest {
    private val participantRepository = mock(LeagueParticipantRepository::class.java)
    private val recordRepository = mock(RunningRecordRepository::class.java)
    private val sessionService = mock(LeagueSessionService::class.java)
    private val userInfoService = mock(UserLeagueInfoService::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val user = User(id = 7)
    private val session = LeagueSession(
        id = 1, tier = LeagueTier(LeagueTierType.BRONZE),
        startDatetime = OffsetDateTime.parse("2025-01-05T15:00:00Z"),
        endDatetime = OffsetDateTime.parse("2025-01-12T14:59:59Z")
    )
    private val participant = LeagueParticipant(id = 11, leagueSession = session, user = user)
    private val participantService = LeagueParticipantService(participantRepository)
    private val leagueService = LeagueService(
        sessionService, participantService, userInfoService, participantRepository, userRepository, recordRepository
    )
    private val mvc = MockMvcBuilders.standaloneSetup(LeagueParticipantController(participantService, leagueService)).build()

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("test-runner", null).apply { details = AuthenticationVo(7) }
        `when`(participantRepository.findCurrentParticipantByUserId(7)).thenReturn(participant)
        `when`(participantRepository.findByUserIdAndSessionId(7, 1)).thenReturn(participant)
        `when`(participantRepository.findById(11)).thenReturn(Optional.of(participant))
        `when`(userRepository.findById(7)).thenReturn(Optional.of(user))
        `when`(userInfoService.getOrCreateUserLeagueInfo(user)).thenReturn(UserLeagueInfo.createNewUser(user))
        `when`(sessionService.getOrCreateActiveSeasonByTier(LeagueTierType.BRONZE)).thenReturn(session)
        `when`(recordRepository.sumIncludedDistanceByUserIdAndEndDatetimeBetween(7, session.startDatetime, session.endDatetime))
            .thenReturn(3000L)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `repeated distance requests use the stored season total instead of adding client distance`() {
        updateDistance(11, 5000).andExpect(status().isOk)
        assertEquals(3000L, participant.totalDistance)
        updateDistance(11, 5000).andExpect(status().isOk)

        assertEquals(3000L, participant.totalDistance)
    }

    @Test
    fun `a request with no stored distance in the current season does not add client distance`() {
        `when`(recordRepository.sumIncludedDistanceByUserIdAndEndDatetimeBetween(7, session.startDatetime, session.endDatetime))
            .thenReturn(0L)

        updateDistance(11, 5000).andExpect(status().isOk)

        assertEquals(0L, participant.totalDistance)
    }

    @Test
    fun `a request for another participant remains forbidden`() {
        updateDistance(12, 5000).andExpect(status().isForbidden)

        assertEquals(0L, participant.totalDistance)
    }

    @Test
    fun `a user without a current participant still receives not found`() {
        `when`(participantRepository.findCurrentParticipantByUserId(7)).thenReturn(null)

        updateDistance(11, 5000).andExpect(status().isNotFound)

        assertEquals(0L, participant.totalDistance)
    }

    private fun updateDistance(participantId: Long, distance: Long) = mvc.perform(
        patch("/api/v1/league-participants/$participantId/distance")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"distance":$distance}""")
    )
}
