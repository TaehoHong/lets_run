package com.example.running.domain.league.scheduler

import com.example.running.domain.league.service.LeagueService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class LeagueScheduler(
    private val leagueService: LeagueService
) {

    /**
     * 매주 월요일 00:00:00 (KST)에 새 시즌 시작
     * - 기존 시즌 종료 처리 (승격/강등 반영)
     * - 새 시즌 생성
     * - 활성 유저 새 시즌에 배정
     * - 봇 보충
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    fun startNewSeasonWeekly() {
        logger.info { "리그 시즌 자동 시작 스케줄러 실행" }

        try {
            val newSeason = leagueService.startNewSeason()
            logger.info { "새 시즌 시작 완료: 시즌 ${newSeason.seasonNumber}" }
        } catch (e: Exception) {
            logger.error(e) { "새 시즌 시작 실패" }
        }
    }
}
