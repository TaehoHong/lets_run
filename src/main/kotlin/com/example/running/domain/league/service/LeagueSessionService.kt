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


    /**
     * 특정 티어의 활성 시즌 조회 또는 생성
     */
    @Transactional(rollbackFor = [Exception::class])
    fun getOrCreateActiveSeasonByTier(tierType: LeagueTierType): LeagueSession {
        val existingSeason = leagueSessionRepository.findByTierIdAndState(tierType.id, LeagueSessionState.ACTIVE)
        if (existingSeason != null) {
            return existingSeason
        }

        // 활성 시즌이 없으면 새로 생성
        val (startDatetime, endDatetime) = calculateSeasonPeriod()
        val newSeason = LeagueSession(
            tier = LeagueTier(tierType),
            startDatetime = startDatetime,
            endDatetime = endDatetime,
            isActive = true,
            state = LeagueSessionState.ACTIVE
        )
        return leagueSessionRepository.save(newSeason).also {
            logger.info { "티어 ${tierType.name} 새 시즌 자동 생성: ${it.id}" }
        }
    }

    /**
     * 러닝 종료 시간을 포함하는 활성 시즌 찾기
     * - 종료 시간 기준으로 시즌 귀속
     */
    @Transactional(readOnly = true)
    fun findActiveSeasonContaining(runningEndedAt: OffsetDateTime, tierType: LeagueTierType): LeagueSession? {
        val activeSeason = leagueSessionRepository.findByTierIdAndState(tierType.id, LeagueSessionState.ACTIVE)
            ?: return null

        // 종료 시간이 시즌 기간 내인지 확인
        if (runningEndedAt.isAfter(activeSeason.startDatetime) &&
            (runningEndedAt.isBefore(activeSeason.endDatetime) || runningEndedAt.isEqual(activeSeason.endDatetime))) {
            return activeSeason
        }

        return null
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
