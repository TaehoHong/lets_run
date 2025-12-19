package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueSeason
import com.example.running.domain.league.repository.LeagueSeasonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Service
class LeagueSeasonService(
    private val leagueSeasonRepository: LeagueSeasonRepository
) {
    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }

    @Transactional(readOnly = true)
    fun getCurrentSeason(): LeagueSeason? {
        return leagueSeasonRepository.findByIsActiveTrue()
    }

    @Transactional(readOnly = true)
    fun getLatestSeason(): LeagueSeason? {
        return leagueSeasonRepository.findTopByOrderBySeasonNumberDesc()
    }

    @Transactional(rollbackFor = [Exception::class])
    fun createNewSeason(): LeagueSeason {
        // 기존 활성 시즌 비활성화
        getCurrentSeason()?.deactivate()

        val nextSeasonNumber = (leagueSeasonRepository.findMaxSeasonNumber() ?: 0) + 1
        val (startDatetime, endDatetime) = calculateSeasonPeriod()

        val newSeason = LeagueSeason(
            seasonNumber = nextSeasonNumber,
            startDatetime = startDatetime,
            endDatetime = endDatetime,
            isActive = true
        )

        return leagueSeasonRepository.save(newSeason)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun endCurrentSeason(): LeagueSeason? {
        val currentSeason = getCurrentSeason() ?: return null
        currentSeason.deactivate()
        return currentSeason
    }

    /**
     * 시즌 기간 계산: 월요일 00:00 ~ 일요일 23:59:59 (KST)
     */
    private fun calculateSeasonPeriod(): Pair<OffsetDateTime, OffsetDateTime> {
        val now = OffsetDateTime.now(KST)

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
        val now = OffsetDateTime.now(KST)
        return java.time.Duration.between(now, endDatetime).toDays()
    }
}
