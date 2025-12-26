package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueGroup
import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.enums.BotType
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.league.service.dto.LeagueParticipantDto
import com.example.running.domain.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

@Service
class LeagueParticipantService(
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val botNameGenerator: BotNameGenerator
) {
    companion object {
        const val PROMOTION_RATE = 0.3
        const val RELEGATION_RATE = 0.2

        // COMPETITOR 봇 거리 범위: 티어 평균의 60~120%
        const val COMPETITOR_MIN_DISTANCE_RATE = 0.6
        const val COMPETITOR_MAX_DISTANCE_RATE = 1.2

        // PACER 봇 거리 범위: 승격 컷 ±5%
        const val PACER_VARIANCE_RATE = 0.05
    }

    @Transactional(rollbackFor = [Exception::class])
    fun addParticipant(group: LeagueGroup, user: User): LeagueParticipant {
        val participant = LeagueParticipant.createParticipant(group, user)
        return leagueParticipantRepository.save(participant)
    }

    /**
     * 봇 추가 (기존 호환성 유지)
     */
    @Transactional(rollbackFor = [Exception::class])
    fun addBot(group: LeagueGroup, averageDistance: Long): LeagueParticipant {
        return addBot(group, averageDistance, BotType.COMPETITOR)
    }

    /**
     * 특정 유형의 봇 추가
     */
    @Transactional(rollbackFor = [Exception::class])
    fun addBot(group: LeagueGroup, averageDistance: Long, botType: BotType): LeagueParticipant {
        val botDistance = calculateBotDistance(averageDistance, botType)
        val botName = botNameGenerator.generate()
        val bot = LeagueParticipant.createBot(group, botDistance, botType, botName)
        return leagueParticipantRepository.save(bot)
    }

    /**
     * 필요한 수만큼 봇 추가 (PACER 30%, COMPETITOR 70% 비율)
     */
    @Transactional(rollbackFor = [Exception::class])
    fun addBots(group: LeagueGroup, count: Int, averageDistance: Long, promotionCutDistance: Long): List<LeagueParticipant> {
        if (count <= 0) return emptyList()

        val (pacerCount, competitorCount) = BotType.calculateDistribution(count)
        val bots = mutableListOf<LeagueParticipant>()

        // PACER 봇 추가 (승격 컷 근처)
        repeat(pacerCount) {
            val distance = calculatePacerDistance(promotionCutDistance)
            val botName = botNameGenerator.generate()
            val bot = LeagueParticipant.createBot(group, distance, BotType.PACER, botName)
            bots.add(leagueParticipantRepository.save(bot))
        }

        // COMPETITOR 봇 추가 (다양한 구간)
        repeat(competitorCount) {
            val distance = calculateCompetitorDistance(averageDistance)
            val botName = botNameGenerator.generate()
            val bot = LeagueParticipant.createBot(group, distance, BotType.COMPETITOR, botName)
            bots.add(leagueParticipantRepository.save(bot))
        }

        return bots
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updateDistance(participantId: Long, distance: Long) {
        val participant = leagueParticipantRepository.findById(participantId)
            .orElseThrow { RuntimeException("참가자를 찾을 수 없습니다: $participantId") }
        participant.addDistance(distance)
    }

    @Transactional(readOnly = true)
    fun getCurrentParticipant(userId: Long): LeagueParticipant? {
        return leagueParticipantRepository.findCurrentParticipantByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun getRankedParticipants(groupId: Long, currentUserId: Long? = null): List<LeagueParticipantDto> {
        val participants = leagueParticipantRepository
            .findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(groupId)

        return participants.mapIndexed { index, participant ->
            LeagueParticipantDto.from(participant, index + 1, currentUserId)
        }
    }

    @Transactional(readOnly = true)
    fun getMyRank(groupId: Long, userId: Long): Int {
        val participants = leagueParticipantRepository
            .findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(groupId)

        return participants.indexOfFirst { it.user?.id == userId } + 1
    }

    @Transactional(rollbackFor = [Exception::class])
    fun processSeasonEnd(groupId: Long, tierType: LeagueTierType): List<LeagueParticipant> {
        val participants = leagueParticipantRepository
            .findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(groupId)

        val totalCount = participants.size
        val promotionCut = ceil(totalCount * PROMOTION_RATE).toInt()
        val relegationCut = totalCount - floor(totalCount * RELEGATION_RATE).toInt()

        participants.forEachIndexed { index, participant ->
            val rank = index + 1
            val status = determinePromotionStatus(rank, promotionCut, relegationCut, tierType)
            participant.setResult(rank, status)
        }

        return participants
    }

    private fun determinePromotionStatus(
        rank: Int,
        promotionCut: Int,
        relegationCut: Int,
        tierType: LeagueTierType
    ): PromotionStatus {
        return when {
            rank <= promotionCut -> {
                if (LeagueTierType.isHighestTier(tierType)) {
                    PromotionStatus.REBIRTH
                } else {
                    PromotionStatus.PROMOTED
                }
            }
            rank > relegationCut && !LeagueTierType.isLowestTier(tierType) -> {
                PromotionStatus.RELEGATED
            }
            else -> PromotionStatus.MAINTAINED
        }
    }

    /**
     * 봇 유형에 따른 거리 계산
     */
    private fun calculateBotDistance(averageDistance: Long, botType: BotType): Long {
        return when (botType) {
            BotType.PACER -> calculatePacerDistance(averageDistance)
            BotType.COMPETITOR -> calculateCompetitorDistance(averageDistance)
        }
    }

    /**
     * PACER 봇 거리 계산: 승격 컷 ±5%
     */
    private fun calculatePacerDistance(promotionCutDistance: Long): Long {
        val minDistance = (promotionCutDistance * (1 - PACER_VARIANCE_RATE)).toLong()
        val maxDistance = (promotionCutDistance * (1 + PACER_VARIANCE_RATE)).toLong()
        return Random.nextLong(minDistance, maxDistance.coerceAtLeast(minDistance + 1))
    }

    /**
     * COMPETITOR 봇 거리 계산: 티어 평균의 60~120%
     */
    private fun calculateCompetitorDistance(averageDistance: Long): Long {
        val minDistance = (averageDistance * COMPETITOR_MIN_DISTANCE_RATE).toLong()
        val maxDistance = (averageDistance * COMPETITOR_MAX_DISTANCE_RATE).toLong()
        return Random.nextLong(minDistance, maxDistance.coerceAtLeast(minDistance + 1))
    }

    @Transactional(readOnly = true)
    fun countParticipants(groupId: Long): Int {
        return leagueParticipantRepository.countByGroupId(groupId)
    }

    @Transactional(readOnly = true)
    fun getAverageDistance(seasonId: Long, tierId: Int): Long {
        return leagueParticipantRepository.findAverageDistanceBySeasonAndTier(seasonId, tierId)?.toLong() ?: 10000L
    }
}
