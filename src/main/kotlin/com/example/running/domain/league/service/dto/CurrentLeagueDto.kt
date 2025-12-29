package com.example.running.domain.league.service.dto

import com.example.running.domain.league.enums.LeagueTierType
import java.time.OffsetDateTime

data class CurrentLeagueDto(
    val tierName: String,
    val sessionId: Long,
    val myRank: Int,
    val totalParticipants: Int,
    val myDistance: Long,
    val promotionCutRank: Int,
    val relegationCutRank: Int,
    val remainingDays: Long,
    val seasonEndDatetime: OffsetDateTime,
    val participants: List<LeagueParticipantDto>
) {
    companion object {
        fun calculatePromotionCutRank(totalParticipants: Int): Int {
            return kotlin.math.ceil(totalParticipants * 0.3).toInt()
        }

        fun calculateRelegationCutRank(totalParticipants: Int, tierType: LeagueTierType): Int {
            // 브론즈는 강등 없음
            if (LeagueTierType.isLowestTier(tierType)) {
                return totalParticipants + 1
            }
            return totalParticipants - kotlin.math.floor(totalParticipants * 0.2).toInt() + 1
        }
    }
}
