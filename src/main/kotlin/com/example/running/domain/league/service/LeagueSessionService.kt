package com.example.running.domain.league.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.LeagueTier
import com.example.running.domain.league.enums.LeagueSessionState
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueSessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

private val logger = KotlinLogging.logger {}

@Service
class LeagueSessionService(
    private val leagueSessionRepository: LeagueSessionRepository
) {
    companion object {
        private val UTC = ZoneOffset.UTC

        // 지연 업로드 허용 시간 (24시간)
        const val LATE_UPLOAD_GRACE_HOURS = 24L

        // Grace Period (15분) - 네트워크 지연 고려
        const val GRACE_PERIOD_MINUTES = 15L
    }

    @Transactional(readOnly = true)
    fun getCurrentSeason(): LeagueSession? {
        return leagueSessionRepository.findByIsActiveTrue()
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): LeagueSession? {
        return leagueSessionRepository.findById(id).orElse(null)
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): LeagueSession {
        return leagueSessionRepository.findById(id).orElseThrow { RuntimeException() }
    }

    @Transactional(readOnly = true)
    fun getAllLeagueSeasonId(state: LeagueSessionState, cursor: Long?, pageSize: Long): CursorResult<Long> {
        val contents = leagueSessionRepository.findAllIdByStatus(state, cursor, pageSize)
        val hasNext = leagueSessionRepository.hasNext(state, contents.lastOrNull())

        return CursorResult(
            contents,
            contents.lastOrNull(),
            hasNext
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    fun createNewSeason(): LeagueSession {

        val (startDatetime, endDatetime) = calculateSeasonPeriod()

        val newSeason = LeagueSession(
            tier = LeagueTier(LeagueTierType.BRONZE),
            startDatetime = startDatetime,
            endDatetime = endDatetime,
            isActive = true,
            state = LeagueSessionState.ACTIVE
        )
        return leagueSessionRepository.save(newSeason)
    }

    // ==================== 시즌 상태 전이 (State Machine) ====================

    /**
     * 시즌 잠금 (일요일 23:59:59 이후)
     * ACTIVE -> LOCKED
     */
    @Transactional(rollbackFor = [Exception::class])
    fun lockSeason(seasonId: Long) {
        val season = findById(seasonId) ?: return

        if (season.state != LeagueSessionState.ACTIVE) {
            throw IllegalStateException("ACTIVE 상태에서만 LOCKED로 전환할 수 있습니다. 현재: ${season.state}")
        }

        season.lock()
        logger.info { "시즌 $seasonId 잠금 처리됨 (LOCKED)" }
    }

    /**
     * 1차 정산 시작 (월요일 00:15)
     * LOCKED -> CALCULATING
     */
    @Transactional(rollbackFor = [Exception::class])
    fun startCalculating(seasonId: Long): LeagueSession {
        val season = findById(seasonId)
            ?: throw RuntimeException("시즌을 찾을 수 없습니다: $seasonId")

        if (season.state != LeagueSessionState.LOCKED) {
            throw IllegalStateException("LOCKED 상태에서만 CALCULATING으로 전환할 수 있습니다. 현재: ${season.state}")
        }

        season.startCalculating()
        logger.info { "시즌 $seasonId 정산 시작 (CALCULATING)" }
        return season
    }

    /**
     * 검수 상태 시작 - 지연 업로드 허용 (월요일 00:15 이후)
     * CALCULATING -> AUDITING
     */
    @Transactional(rollbackFor = [Exception::class])
    fun startAuditing(seasonId: Long): LeagueSession {
        val season = findById(seasonId)
            ?: throw RuntimeException("시즌을 찾을 수 없습니다: $seasonId")

        if (season.state != LeagueSessionState.CALCULATING) {
            throw IllegalStateException("CALCULATING 상태에서만 AUDITING으로 전환할 수 있습니다. 현재: ${season.state}")
        }

        season.startAuditing()
        logger.info { "시즌 $seasonId 검수 시작 (AUDITING)" }
        return season
    }

    /**
     * 최종 확정 (화요일 00:00)
     * AUDITING -> FINALIZED
     */
    @Transactional(rollbackFor = [Exception::class])
    fun finalizeSeason(seasonId: Long): LeagueSession {
        val season = findById(seasonId)
            ?: throw RuntimeException("시즌을 찾을 수 없습니다: $seasonId")

        if (season.state != LeagueSessionState.AUDITING) {
            throw IllegalStateException("AUDITING 상태에서만 FINALIZED로 전환할 수 있습니다. 현재: ${season.state}")
        }

        season.finalize()
        logger.info { "시즌 $seasonId 최종 확정 (FINALIZED)" }
        return season
    }

    // ==================== 러닝 기록 귀속 검증 ====================

    /**
     * 러닝 기록이 현재 시즌에 귀속 가능한지 확인
     * - 러닝 시작 시간 기준으로 시즌 귀속
     * - Grace Period: 15분
     * - 지연 업로드: 시즌 종료 후 24시간까지
     */
//    fun canAcceptRunningRecord(runningStartedAt: OffsetDateTime): Pair<Boolean, LeagueSession?> {
//        val currentSeason = getCurrentSeason()
//
//        // 활성 시즌이 있고, 러닝 시작 시간이 시즌 기간 내인 경우
//        if (currentSeason != null && currentSeason.canAcceptNewRecords()) {
//            val seasonStart = currentSeason.startDatetime.minusMinutes(GRACE_PERIOD_MINUTES)
//            val seasonEnd = currentSeason.endDatetime.plusMinutes(GRACE_PERIOD_MINUTES)
//
//            if (runningStartedAt.isAfter(seasonStart) && runningStartedAt.isBefore(seasonEnd)) {
//                return Pair(true, currentSeason)
//            }
//        }
//
//        // AUDITING 상태에서 지연 업로드 허용
//        val latestSeason = getLatestSeason()
//        if (latestSeason != null && latestSeason.canAcceptLateRecords()) {
//            val uploadDeadline = latestSeason.endDatetime.plusHours(LATE_UPLOAD_GRACE_HOURS)
//            val now = OffsetDateTime.now(UTC)
//
//            if (now.isBefore(uploadDeadline)) {
//                // 러닝 시작 시간이 해당 시즌 기간 내인지 확인
//                val seasonStart = latestSeason.startDatetime.minusMinutes(GRACE_PERIOD_MINUTES)
//                val seasonEnd = latestSeason.endDatetime.plusMinutes(GRACE_PERIOD_MINUTES)
//
//                if (runningStartedAt.isAfter(seasonStart) && runningStartedAt.isBefore(seasonEnd)) {
//                    return Pair(true, latestSeason)
//                }
//            }
//        }
//
//        return Pair(false, null)
//    }

    /**
     * 시즌 기간 계산: 월요일 00:00 ~ 일요일 23:59:59 (KST)
     */
    private fun calculateSeasonPeriod(): Pair<OffsetDateTime, OffsetDateTime> {
        val now = OffsetDateTime.now(UTC)

        // 이번 주 월요일 00:00
        val startDatetime = now
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // 이번 주 일요일 23:59:59
        val endDatetime = now
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(0)

        return Pair(startDatetime, endDatetime)
    }

    fun calculateRemainingDays(endDatetime: OffsetDateTime): Long {
        val now = OffsetDateTime.now(UTC)
        return Duration.between(now, endDatetime).toDays()
    }

    fun calculateRemainingHours(endDatetime: OffsetDateTime): Long {
        val now = OffsetDateTime.now(UTC)
        return Duration.between(now, endDatetime).toHours()
    }
}
