package com.example.running.domain.league.scheduler

import com.example.running.domain.league.enums.LeagueSessionState
import com.example.running.domain.league.service.LeagueSessionService
import com.example.running.domain.league.service.LeagueSettlementService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class LeagueScheduler(
    private val leagueSessionService: LeagueSessionService,
    private val leagueSettlementService: LeagueSettlementService,
) {


    /**
     * 일요일 23:59:59 - 시즌 잠금
     */
//    @Scheduled(cron = "59 59 23 * * SUN", zone = "Asia/Seoul")
    @Scheduled(cron = "00 24 14 * * MON", zone = "Asia/Seoul")
    fun lockSeasonOnSunday() {
        logger.info { "시즌 잠금 스케줄러 실행" }

        processPerSession(LeagueSessionState.ACTIVE) { sessionId ->
            leagueSessionService.lockSeason(sessionId)
            logger.info { "시즌 $sessionId 잠금 완료" }
        }
    }

    /**
     * 월요일 00:15 - 정산
     */
//    @Scheduled(cron = "0 15 0 * * MON", zone = "Asia/Seoul")
    @Scheduled(cron = "00 25 14 * * MON", zone = "Asia/Seoul")
    fun primarySettlementOnMonday() {
        logger.info { "정산 스케줄러 실행" }

        processPerSession(LeagueSessionState.LOCKED) { sessionId ->
            leagueSettlementService.executePrimarySettlement(sessionId)
            logger.info { "세션 $sessionId 정산 완료" }
        }
    }

    /**
     * 화요일 00:00 - 최종 확정
     * AUDITING 상태인 이전 시즌을 FINALIZED로 전환
     */
    @Scheduled(cron = "0 0 0 * * TUE", zone = "Asia/Seoul")
    fun finalizeSeasonOnTuesday() {
        logger.info { "최종 확정 스케줄러 실행" }

        processPerSession(LeagueSessionState.AUDITING) { sessionId ->
            val session = leagueSessionService.getById(sessionId)
            leagueSettlementService.executeFinalSettlement(session)
            logger.info { "시즌 $sessionId 최종 확정 완료" }
        }
    }

    private fun processPerSession(state: LeagueSessionState, function: (Long) -> Unit) {
        var cursor: Long? = null
        var hasNext = true

        try {
            while (hasNext) {
                val sessionIdPage = leagueSessionService.getAllLeagueSeasonId(state, cursor, 10)
                hasNext = sessionIdPage.hasNext
                cursor = sessionIdPage.cursor

                sessionIdPage.content.forEach { sessionId ->
                    function(sessionId)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "스케줄링 실패" }
        }
    }
}
