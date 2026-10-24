package com.example.running.domain.running.controller

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.LeagueTier
import com.example.running.domain.league.entity.UserLeagueInfo
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.league.service.LeagueParticipantService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.league.service.LeagueSessionService
import com.example.running.domain.league.service.UserLeagueInfoService
import com.example.running.domain.point.entity.UserPoint
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.RunningEndService
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.domain.running.service.RunningStartService
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.security.vo.AuthenticationVo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional

class RunningRecordControllerEndTimestampTest {
    private val repository = mock(RunningRecordRepository::class.java)
    private val pointService = mock(UserPointService::class.java)
    private val record = RunningRecord(
        id = 77, user = User(id = 7), startDatetime = OffsetDateTime.parse("2025-01-05T14:30:00Z")
    )
    private val recordService = RunningRecordService(repository, mock(ShoeService::class.java))
    private val participantRepository = mock(LeagueParticipantRepository::class.java)
    private val sessionService = mock(LeagueSessionService::class.java)
    private val userLeagueInfoService = mock(UserLeagueInfoService::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val session = LeagueSession(
        id = 1, tier = LeagueTier(LeagueTierType.BRONZE),
        startDatetime = OffsetDateTime.parse("2025-01-05T15:00:00Z"),
        endDatetime = OffsetDateTime.parse("2025-01-12T14:59:59Z")
    )
    private val participant = LeagueParticipant(id = 11, leagueSession = session, user = record.user)
    private val leagueService = LeagueService(
        sessionService, LeagueParticipantService(participantRepository), userLeagueInfoService,
        participantRepository, userRepository, repository
    )
    private val controller = RunningRecordController(
        mock(RunningStartService::class.java),
        RunningEndService(recordService, pointService, repository, leagueService, mock(NotificationService::class.java)),
        recordService
    )
    private val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val mvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
        .build()
    private val request: Map<String, Number> = mapOf(
        "distance" to 5000, "durationSec" to 1800, "cadence" to 170, "heartRate" to 150, "calorie" to 100
    )

    @BeforeEach
    fun setUp() {
        `when`(repository.findByIdAndUserId(77, 7)).thenReturn(record)
        `when`(pointService.getOrCreateByUserId(7)).thenReturn(UserPoint(userId = 7, point = 50))
        `when`(userRepository.findById(7)).thenReturn(Optional.of(record.user))
        `when`(userLeagueInfoService.getOrCreateUserLeagueInfo(record.user))
            .thenReturn(UserLeagueInfo.createNewUser(record.user))
        `when`(sessionService.getOrCreateActiveSeasonByTier(LeagueTierType.BRONZE)).thenReturn(session)
        `when`(participantRepository.findByUserIdAndSessionId(7, 1)).thenReturn(participant)
        `when`(participantRepository.findById(11)).thenReturn(Optional.of(participant))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("test-runner", null).apply { details = AuthenticationVo(7) }
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `the end request preserves Monday completion time for a run started on Sunday`() {
        endRecord(request + ("endTimestamp" to 1736091000L))

        assertEquals(OffsetDateTime.parse("2025-01-05T15:30:00Z"), record.endDatetime)
        assertEquals(OffsetDateTime.parse("2025-01-05T14:30:00Z"), record.startDatetime)
        assertEquals(DayOfWeek.MONDAY, record.endDatetime?.atZoneSameInstant(ZoneId.of("Asia/Seoul"))?.dayOfWeek)
    }

    @Test
    fun `retrying the end request preserves the original completion time`() {
        `when`(repository.sumIncludedDistanceByUserIdAndEndDatetimeBetween(7, session.startDatetime, session.endDatetime))
            .thenReturn(7000L)
        val payload = request + ("endTimestamp" to 1736091000L)
        endRecord(payload)
        endRecord(payload)

        assertEquals(OffsetDateTime.parse("2025-01-05T15:30:00Z"), record.endDatetime)
        assertEquals(7000L, participant.totalDistance)
    }

    @Test
    fun `legacy retry without timestamp does not move the reminder anchor`() {
        endRecord(request + ("endTimestamp" to 1736091000L))
        endRecord(request)
        assertEquals(OffsetDateTime.parse("2025-01-05T15:30:00Z"), record.endDatetime)
    }

    @Test
    fun `completing a synced record updates league distance without the confirmation screen`() {
        `when`(repository.sumIncludedDistanceByUserIdAndEndDatetimeBetween(7, session.startDatetime, session.endDatetime))
            .thenReturn(7000L)

        endRecord(request + ("endTimestamp" to 1736091000L))

        assertEquals(7000L, participant.totalDistance)
        assertEquals(5000, record.distance)
    }

    @Test
    fun `a legacy request without an end timestamp still uses server time`() {
        val before = OffsetDateTime.now(ZoneOffset.UTC)
        endRecord(request)
        val after = OffsetDateTime.now(ZoneOffset.UTC)

        val endedAt = requireNotNull(record.endDatetime)
        assertFalse(endedAt.isBefore(before))
        assertFalse(endedAt.isAfter(after))
    }

    private fun endRecord(payload: Map<String, Number>) {
        mvc.perform(post("/api/v1/running/77/end")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(payload)))
            .andExpect(status().isOk)
    }
}
