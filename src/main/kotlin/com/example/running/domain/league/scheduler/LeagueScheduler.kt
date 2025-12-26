package com.example.running.domain.league.scheduler

import com.example.running.domain.league.entity.LeagueSeason
import com.example.running.domain.league.enums.SeasonState
import com.example.running.domain.league.service.BotManagementService
import com.example.running.domain.league.service.LeagueSeasonService
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.league.service.LeagueSettlementService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 리그 스케줄러
 *
 * 정산 스케줄:
 * - 일요일 23:59:59: 시즌 종료 (LOCKED)
 * - 월요일 00:15: 1차 정산, Protected 플래그 설정
 * - 매 30분: 봇 기록 갱신 + 점진적 봇 투입 (3~24시간) + 이전 시즌 확정 (24시간 후)
 *
 * 새 시즌 생성 및 리그 참여는 사용자가 그 주에 첫 러닝 완료 시 처리됨
 *
 * @see <a href="https://www.notion.so/2cc405e9dd388175bf1cf008612a3876">리그 기획서</a>
 */
@Component
class LeagueScheduler(
    private val leagueService: LeagueService,
    private val leagueSeasonService: LeagueSeasonService,
    private val leagueSettlementService: LeagueSettlementService,
    private val botManagementService: BotManagementService
) {

    // ==================== 시즌 종료 처리 ====================

    /**
     * 일요일 23:59:59 - 시즌 잠금
     */
    @Scheduled(cron = "59 59 23 * * SUN", zone = "Asia/Seoul")
    fun lockSeasonOnSunday() {
        logger.info { "시즌 잠금 스케줄러 실행" }

        try {
            val currentSeason = leagueSeasonService.getCurrentSeason()
            if (currentSeason != null && currentSeason.state == SeasonState.ACTIVE) {
                leagueSeasonService.lockSeason(currentSeason.id)
                logger.info { "시즌 ${currentSeason.seasonNumber} 잠금 완료" }
            }
        } catch (e: Exception) {
            logger.error(e) { "시즌 잠금 실패" }
        }
    }

    /**
     * 월요일 00:15 - 1차 정산
     */
    @Scheduled(cron = "0 15 0 * * MON", zone = "Asia/Seoul")
    fun primarySettlementOnMonday() {
        logger.info { "1차 정산 스케줄러 실행" }

        try {
            val latestSeason = leagueSeasonService.getLatestSeason()
            if (latestSeason != null && latestSeason.state == SeasonState.LOCKED) {
                leagueSettlementService.executePrimarySettlement(latestSeason)
                logger.info { "시즌 ${latestSeason.seasonNumber} 1차 정산 완료" }
            }
        } catch (e: Exception) {
            logger.error(e) { "1차 정산 실패" }
        }
    }

    // ==================== 30분 단위 봇 관리 ====================

    /**
     * 매 30분 - 봇 기록 갱신, 점진적 봇 투입, 이전 시즌 확정
     * 1. 슬롯 기반 봇 기록 갱신 (각 봇은 하루에 한 번)
     * 2. 점진적 봇 투입 (시즌 시작 3~24시간, t² 곡선)
     * 3. 이전 시즌 확정 (시즌 시작 24시간 후)
     */
    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Seoul")
    fun updateBotsAndInjectIfNeeded() {
        try {
            val currentSeason = leagueSeasonService.getCurrentSeason()
            if (currentSeason != null && currentSeason.state == SeasonState.ACTIVE) {
                // 1. 봇 기록 갱신
                botManagementService.updateBotDistancesBySlot(currentSeason)

                // 2. 점진적 봇 투입
                botManagementService.executeProgressiveBotInjection(currentSeason)

                // 3. 이전 시즌 확정 (시즌 시작 24시간 후)
                finalizePreviousSeasonIfNeeded(currentSeason)
            }
        } catch (e: Exception) {
            logger.error(e) { "봇 관리 스케줄러 실패" }
        }
    }

    /**
     * 이전 시즌 확정 (현재 시즌 시작 24시간 후)
     */
    private fun finalizePreviousSeasonIfNeeded(currentSeason: LeagueSeason) {
        try {
            val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            val elapsedHours = java.time.Duration.between(currentSeason.startDatetime, now).toHours()

            // 24시간 이전이면 확정하지 않음
            if (elapsedHours < 24) {
                return
            }

            // AUDITING 상태인 이전 시즌 확정
            val previousSeason = leagueSeasonService.getLatestSeason()
            if (previousSeason != null && previousSeason.state == SeasonState.AUDITING) {
                leagueSettlementService.executeFinalSettlement(previousSeason)
                logger.info { "시즌 ${previousSeason.seasonNumber} 최종 확정 완료 (시즌 시작 ${elapsedHours}시간 후)" }
            }
        } catch (e: Exception) {
            logger.error(e) { "이전 시즌 확정 실패" }
        }
    }

    // ==================== 장기 미접속 유저 처리 ====================

    /**
     * 매주 월요일 01:00 - 장기 미접속 유저 처리
     */
    @Scheduled(cron = "0 0 1 * * MON", zone = "Asia/Seoul")
    fun handleInactiveUsersWeekly() {
        logger.info { "장기 미접속 유저 처리 스케줄러 실행" }

        try {
            leagueService.processInactiveUsers()
            logger.info { "장기 미접속 유저 처리 완료" }
        } catch (e: Exception) {
            logger.error(e) { "장기 미접속 유저 처리 실패" }
        }
    }
}
