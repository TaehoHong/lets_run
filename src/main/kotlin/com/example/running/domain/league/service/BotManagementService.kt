package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.enums.BotType
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueParticipantRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration.between
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

@Service
class BotManagementService(
    private val leagueSessionService: LeagueSessionService,
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val leagueParticipantService: LeagueParticipantService,
    private val botNameGenerator: BotNameGenerator
) {
    companion object {
        private val UTC = ZoneOffset.UTC

        // 최소 인원 (봇 투입 목표)
        const val MINIMUM_PARTICIPANTS = 20

        // 봇 투입 시작 시간 (시즌 시작 후 N시간)
        const val BOT_INJECTION_START_HOURS = 3.0

        // 봇 투입 종료 시간 (시즌 시작 후 N시간, 이후 즉시 20명 채움)
        const val BOT_INJECTION_END_HOURS = 24.0

        // 봇 업데이트 슬롯 수 (30분 단위, 하루 48슬롯)
        const val TOTAL_SLOTS = 48
    }

    // ==================== 점진적 봇 투입 ====================

    /**
     * 점진적 봇 투입 (30분마다 실행)
     * - 시즌 시작 3시간 후부터 24시간까지 제곱 곡선(t²)으로 투입
     * - 24시간 이후: 20명 미달 시 즉시 20명까지 채움
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executeProgressiveBotInjection(sessionId: Long) {
        val leagueSession = leagueSessionService.getById(sessionId)
        val now = OffsetDateTime.now(UTC)
        val elapsedHours = between(leagueSession.createdDatetime, now).toMinutes() / 60.0

        // 3시간 이전: 투입 안함
        if (elapsedHours < BOT_INJECTION_START_HOURS) {
            logger.debug { "봇 투입 대기: 경과 ${String.format("%.1f", elapsedHours)}시간 (시작: ${BOT_INJECTION_START_HOURS}시간 후)" }
            return
        }

        val currentParticipants = leagueParticipantService.countParticipants(leagueSession.id)
        val currentBots = leagueParticipantRepository.findBotsByGroupId(leagueSession.id).size
        val realParticipants = currentParticipants - currentBots

        // 20명 이상이면 투입 불필요
        if (currentParticipants >= MINIMUM_PARTICIPANTS) {
            return
        }

        // 필요한 총 봇 수 (20명 - 실제 유저)
        val totalBotsNeeded = (MINIMUM_PARTICIPANTS - realParticipants).coerceAtLeast(0)

        // 현재 시점에서 투입해야 할 목표 봇 수
        val targetBotCount = calculateTargetBotCount(elapsedHours, totalBotsNeeded)

        // 추가로 투입할 봇 수
        val botsToAdd = (targetBotCount - currentBots).coerceAtLeast(0)

        if (botsToAdd > 0) {
            injectBots(leagueSession, botsToAdd, LeagueTierType.fromId(leagueSession.tier.id), elapsedHours)
            logger.info { "그룹 ${leagueSession.id} (${LeagueTierType.fromId(leagueSession.tier.id)}): 봇 ${botsToAdd}명 투입 (현재 ${currentBots} → ${currentBots + botsToAdd}, 목표 $totalBotsNeeded, 경과 ${String.format("%.1f", elapsedHours)}시간)" }
        }
    }

    /**
     * 제곱 곡선 기반 목표 봇 수 계산
     * - 3시간 ~ 24시간: t² 곡선으로 점진적 증가
     * - 24시간 이후: 전체 필요 봇 수 반환 (즉시 채움)
     */
    private fun calculateTargetBotCount(elapsedHours: Double, totalBotsNeeded: Int): Int {
        // 24시간 이후: 전체 필요 봇 수
        if (elapsedHours >= BOT_INJECTION_END_HOURS) {
            return totalBotsNeeded
        }

        // 3시간 ~ 24시간: 제곱 곡선
        // 진행률 t = (경과시간 - 3) / (24 - 3) = (경과시간 - 3) / 21
        val windowDuration = BOT_INJECTION_END_HOURS - BOT_INJECTION_START_HOURS
        val progress = ((elapsedHours - BOT_INJECTION_START_HOURS) / windowDuration).coerceIn(0.0, 1.0)

        // 제곱 곡선: 목표 봇 수 = 총 필요 봇 × t²
        val targetRatio = progress * progress
        return (totalBotsNeeded * targetRatio).toInt()
    }

    /**
     * 봇 투입
     * @param elapsedHours 시즌 시작 후 경과 시간 (초기 거리 계산에 사용)
     */
    private fun injectBots(
        session: LeagueSession,
        count: Int,
        tierType: LeagueTierType,
        elapsedHours: Double
    ) {
        val targetDistance = BotBalancePolicy.targetDistanceForTier(tierType)
        val promotionCutDistance = BotBalancePolicy.promotionCutDistance(targetDistance)

        val (pacerCount, competitorCount) = BotType.calculateDistribution(count)

        // PACER 봇 추가
        repeat(pacerCount) {
            val finalDistance = BotBalancePolicy.pacerFinalDistance(promotionCutDistance)
            val currentDistance = calculateInitialBotDistance(finalDistance, elapsedHours)
            val botName = botNameGenerator.generate()
            val slot = Random.nextInt(0, TOTAL_SLOTS)

            val bot = LeagueParticipant.createBot(session, currentDistance, BotType.PACER, botName, slot)
            leagueParticipantRepository.save(bot)
        }

        // COMPETITOR 봇 추가
        repeat(competitorCount) {
            val finalDistance = BotBalancePolicy.competitorFinalDistance(targetDistance)
            val currentDistance = calculateInitialBotDistance(finalDistance, elapsedHours)
            val botName = botNameGenerator.generate()
            val slot = Random.nextInt(0, TOTAL_SLOTS)

            val bot = LeagueParticipant.createBot(session, currentDistance, BotType.COMPETITOR, botName, slot)
            leagueParticipantRepository.save(bot)
        }
    }

    /**
     * 봇 초기 거리 계산 (투입 시점의 경과 시간 기반)
     * - 경과 시간에 비례하여 일별 진행률 적용
     */
    private fun calculateInitialBotDistance(finalDistance: Long, elapsedHours: Double): Long {
        val progressRange = BotBalancePolicy.progressRangeForElapsedHours(elapsedHours)
        val progress = Random.nextDouble(progressRange.first / 100.0, progressRange.last / 100.0)

        return (finalDistance * progress).toLong()
    }

    // ==================== 봇 기록 갱신 ====================

    /**
     * 슬롯 기반 봇 기록 갱신
     * - 30분마다 실행
     * - 현재 슬롯에 해당하는 봇만 업데이트
     * - 각 봇은 하루에 한 번만 업데이트됨
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateBotDistancesBySlot(seasonId: Long) {

        val session = leagueSessionService.getById(seasonId)
        val now = OffsetDateTime.now(UTC)
        val updateWindow = BotBalancePolicy.dailyUpdateWindow(now)
        val progressRange = updateWindow.progressRange
        val minProgress = progressRange.first / 100.0
        val maxProgress = progressRange.last / 100.0

        // 해당 슬롯에서 오늘 아직 업데이트되지 않은 봇만 조회
        val botsToUpdate = leagueParticipantRepository.findBotsToUpdateBySlot(
            seasonId,
            updateWindow.slot,
            updateWindow.today
        )

        if (botsToUpdate.isEmpty()) {
            logger.debug { "슬롯 ${updateWindow.slot}: 업데이트할 봇 없음" }
            return
        }

        logger.info { "봇 기록 갱신: 슬롯 ${updateWindow.slot}, ${updateWindow.dayOfWeek}, 대상 ${botsToUpdate.size}명, 진행률 ${progressRange.first}~${progressRange.last}%" }

        botsToUpdate.forEach { bot ->
            val tierType = LeagueTierType.fromId(session.tier.id)
            val targetDistance = BotBalancePolicy.targetDistanceForTier(tierType)
            val promotionCutDistance = BotBalancePolicy.promotionCutDistance(targetDistance)

            val finalDistance = calculateBotFinalDistance(bot.botType, targetDistance, promotionCutDistance)
            val progress = Random.nextDouble(minProgress, maxProgress)
            val newDistance = (finalDistance * progress).toLong()

            bot.updateBotDistance(newDistance, updateWindow.today)
        }

        logger.info { "봇 기록 갱신 완료: 슬롯 ${updateWindow.slot}, ${botsToUpdate.size}명 업데이트" }
    }

    /**
     * 봇 유형별 최종 거리 계산
     */
    private fun calculateBotFinalDistance(
        botType: BotType?,
        targetDistance: Long,
        promotionCutDistance: Long
    ): Long {
        return when (botType) {
            BotType.PACER -> BotBalancePolicy.pacerFinalDistance(promotionCutDistance)
            BotType.COMPETITOR -> BotBalancePolicy.competitorFinalDistance(targetDistance)
            null -> BotBalancePolicy.competitorFinalDistance(targetDistance) // 기본값
        }
    }

}
