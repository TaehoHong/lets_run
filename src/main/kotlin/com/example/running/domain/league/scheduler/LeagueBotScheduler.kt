package com.example.running.domain.league.scheduler

import com.example.running.domain.league.enums.LeagueSessionState
import com.example.running.domain.league.service.BotManagementService
import com.example.running.domain.league.service.LeagueSessionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}


@Component
class LeagueBotScheduler(
    private val leagueSessionService: LeagueSessionService,
    private val botManagementService: BotManagementService
) {

    /**
     * 매 30분 - 봇 기록 갱신
     * 슬롯 기반 봇 기록 갱신 (각 봇은 하루에 한 번)
     */
    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Seoul")
    fun updateBotDistance() {
        processPerSession { sessionId ->
            botManagementService.updateBotDistancesBySlot(sessionId)
        }
    }


    /**
     * 봇 투입 (시즌 시작 3~24시간, t² 곡선)
     */
    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Seoul")
    fun injectBotToLeagueSession() {
        processPerSession { sessionId ->
            botManagementService.executeProgressiveBotInjection(sessionId)
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
