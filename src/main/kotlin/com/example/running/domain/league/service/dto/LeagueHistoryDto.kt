package com.example.running.domain.league.service.dto

import com.example.running.domain.league.entity.LeagueParticipant

data class LeagueHistoryDto(
    val tier: String,
    val rank: Int?,
    val totalParticipants: Int,
    val totalDistance: Long,
    val promotionStatus: String?
) {
    companion object {
        fun from(participant: LeagueParticipant, totalParticipants: Int): LeagueHistoryDto {
            return LeagueHistoryDto(
                tier = participant.leagueSession.tier.name,
                rank = participant.finalRank,
                totalParticipants = totalParticipants,
                totalDistance = participant.totalDistance,
                promotionStatus = participant.promotionStatus?.name
            )
        }
    }
}
