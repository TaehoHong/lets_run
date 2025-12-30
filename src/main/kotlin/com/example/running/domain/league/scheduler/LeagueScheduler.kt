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
     * 일요일 23:59:59 - 시즌 종료 + 즉시 정산 + 확정
     * ACTIVE → FINALIZED 직접 전환
     */
    @Scheduled(cron = "59 59 23 * * SUN", zone = "Asia/Seoul")
    fun endSeason() {
        logger.info { "시즌 종료 스케줄러 실행" }

        processPerSession { sessionId ->
            leagueSettlementService.executeImmediateSettlement(sessionId)
            logger.info { "시즌 $sessionId 정산 및 확정 완료" }
        }
    }

    private fun processPerSession(function: (Long) -> Unit) {
        var cursor: Long? = null
        var hasNext = true

        try {
            while (hasNext) {
                val sessionIdPage = leagueSessionService.getAllLeagueSeasonId(LeagueSessionState.ACTIVE, cursor, 10)
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
