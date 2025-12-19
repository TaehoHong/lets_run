package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueGroup
import com.example.running.domain.league.entity.LeagueParticipant
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
    private val leagueParticipantRepository: LeagueParticipantRepository
) {
    companion object {
        const val PROMOTION_RATE = 0.3
        const val RELEGATION_RATE = 0.2
        const val BOT_MIN_DISTANCE_RATE = 0.5
        const val BOT_MAX_DISTANCE_RATE = 1.0
    }

    @Transactional(rollbackFor = [Exception::class])
    fun addParticipant(group: LeagueGroup, user: User): LeagueParticipant {
        val participant = LeagueParticipant.createParticipant(group, user)
        return leagueParticipantRepository.save(participant)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun addBot(group: LeagueGroup, averageDistance: Long): LeagueParticipant {
        val botDistance = calculateBotDistance(averageDistance)
        val bot = LeagueParticipant.createBot(group, botDistance)
        return leagueParticipantRepository.save(bot)
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

    private fun calculateBotDistance(averageDistance: Long): Long {
        val minDistance = (averageDistance * BOT_MIN_DISTANCE_RATE).toLong()
        val maxDistance = (averageDistance * BOT_MAX_DISTANCE_RATE).toLong()
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
