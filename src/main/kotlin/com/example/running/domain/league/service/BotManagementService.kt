package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.enums.BotType
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueParticipantRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Duration.between
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * 봇 관리 서비스
 *
 * 노션 기획서 봇 정책:
 * - 점진적 봇 투입: 시즌 시작 3시간 후부터 24시간까지 제곱 곡선(t²)으로 투입
 * - 24시간 이후: 20명 미달 시 즉시 20명까지 채움
 * - 시즌 중 봇은 절대 제거하지 않음
 *
 * @see <a href="https://www.notion.so/2cc405e9dd388175bf1cf008612a3876">리그 기획서</a>
 */
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

        // 일별 진행률 범위 (%)
        val DAILY_PROGRESS_RATES = mapOf(
            DayOfWeek.MONDAY to (10..15),
            DayOfWeek.TUESDAY to (25..35),
            DayOfWeek.WEDNESDAY to (40..50),
            DayOfWeek.THURSDAY to (55..65),
            DayOfWeek.FRIDAY to (70..80),
            DayOfWeek.SATURDAY to (85..95),
            DayOfWeek.SUNDAY to (100..100)
        )
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
        val averageDistance = getAverageDistanceForTier(tierType)
        val promotionCutDistance = calculatePromotionCutDistance(averageDistance)

        val (pacerCount, competitorCount) = BotType.calculateDistribution(count)

        // PACER 봇 추가
        repeat(pacerCount) {
            val finalDistance = calculatePacerFinalDistance(promotionCutDistance)
            val currentDistance = calculateInitialBotDistance(finalDistance, elapsedHours)
            val botName = botNameGenerator.generate()
            val slot = Random.nextInt(0, TOTAL_SLOTS)

            val bot = LeagueParticipant.createBot(session, currentDistance, BotType.PACER, botName, slot)
            leagueParticipantRepository.save(bot)
        }

        // COMPETITOR 봇 추가
        repeat(competitorCount) {
            val finalDistance = calculateCompetitorFinalDistance(averageDistance)
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
        // 시즌 7일 = 168시간 기준 진행률 계산
        val dayProgress = (elapsedHours / 24.0).coerceIn(0.0, 7.0)
        val dayOfWeek = when {
            dayProgress < 1 -> DayOfWeek.MONDAY
            dayProgress < 2 -> DayOfWeek.TUESDAY
            dayProgress < 3 -> DayOfWeek.WEDNESDAY
            dayProgress < 4 -> DayOfWeek.THURSDAY
            dayProgress < 5 -> DayOfWeek.FRIDAY
            dayProgress < 6 -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }

        val progressRange = DAILY_PROGRESS_RATES[dayOfWeek] ?: (10..15)
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
        val today = now.toLocalDate()
        val dayOfWeek = now.dayOfWeek
        val currentSlot = calculateCurrentSlot(now)

        val progressRange = DAILY_PROGRESS_RATES[dayOfWeek] ?: return
        val minProgress = progressRange.first / 100.0
        val maxProgress = progressRange.last / 100.0

        // 해당 슬롯에서 오늘 아직 업데이트되지 않은 봇만 조회
        val botsToUpdate = leagueParticipantRepository.findBotsToUpdateBySlot(seasonId, currentSlot, today)

        if (botsToUpdate.isEmpty()) {
            logger.debug { "슬롯 $currentSlot: 업데이트할 봇 없음" }
            return
        }

        logger.info { "봇 기록 갱신: 슬롯 $currentSlot, ${dayOfWeek}, 대상 ${botsToUpdate.size}명, 진행률 ${progressRange.first}~${progressRange.last}%" }

        botsToUpdate.forEach { bot ->
            val tierType = LeagueTierType.fromId(session.tier.id)
            val averageDistance = getAverageDistanceForTier(tierType)
            val promotionCutDistance = calculatePromotionCutDistance(averageDistance)

            val finalDistance = calculateBotFinalDistance(bot.botType, averageDistance, promotionCutDistance)
            val progress = Random.nextDouble(minProgress, maxProgress)
            val newDistance = (finalDistance * progress).toLong()

            bot.updateBotDistance(newDistance, today)
        }

        logger.info { "봇 기록 갱신 완료: 슬롯 $currentSlot, ${botsToUpdate.size}명 업데이트" }
    }

    /**
     * 현재 시간의 슬롯 계산 (0-47)
     * - 00:00~00:29 → 슬롯 0
     * - 00:30~00:59 → 슬롯 1
     * - ...
     * - 23:30~23:59 → 슬롯 47
     */
    private fun calculateCurrentSlot(now: OffsetDateTime): Int {
        val hour = now.hour
        val minute = now.minute
        return (hour * 2) + (minute / 30)
    }

    /**
     * 티어별 평균 거리 조회
     */
    private fun getAverageDistanceForTier(tierType: LeagueTierType): Long {
        // 티어별 기본 평균 거리 (미터)
        return when (tierType) {
            LeagueTierType.BRONZE -> 15_000L      // 15km
            LeagueTierType.SILVER -> 25_000L     // 25km
            LeagueTierType.GOLD -> 40_000L       // 40km
            LeagueTierType.PLATINUM -> 60_000L   // 60km
            LeagueTierType.DIAMOND -> 80_000L    // 80km
            LeagueTierType.CHALLENGER -> 100_000L // 100km
        }
    }

    /**
     * 승격 컷라인 거리 계산
     */
    private fun calculatePromotionCutDistance(averageDistance: Long): Long {
        // 상위 30%에 해당하는 거리 (평균의 약 130%)
        return (averageDistance * 1.3).toLong()
    }

    /**
     * PACER 봇 최종 거리 계산
     */
    private fun calculatePacerFinalDistance(promotionCutDistance: Long): Long {
        val variance = Random.nextDouble(-0.05, 0.05)
        return (promotionCutDistance * (1 + variance)).toLong()
    }

    /**
     * COMPETITOR 봇 최종 거리 계산
     */
    private fun calculateCompetitorFinalDistance(averageDistance: Long): Long {
        val multiplier = Random.nextDouble(0.6, 1.2)
        return (averageDistance * multiplier).toLong()
    }

    /**
     * 봇 유형별 최종 거리 계산
     */
    private fun calculateBotFinalDistance(
        botType: BotType?,
        averageDistance: Long,
        promotionCutDistance: Long
    ): Long {
        return when (botType) {
            BotType.PACER -> calculatePacerFinalDistance(promotionCutDistance)
            BotType.COMPETITOR -> calculateCompetitorFinalDistance(averageDistance)
            null -> calculateCompetitorFinalDistance(averageDistance) // 기본값
        }
    }

}
