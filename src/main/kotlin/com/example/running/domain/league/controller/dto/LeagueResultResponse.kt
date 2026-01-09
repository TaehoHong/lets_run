package com.example.running.domain.league.controller.dto

import com.example.running.domain.league.service.dto.LeagueResultDto

/**
 * 리그 시즌 결과 응답 DTO
 */
data class LeagueResultResponse(
    val previousTier: String,
    val currentTier: String,
    val resultStatus: String,
    val finalRank: Int,
    val totalParticipants: Int,
    val totalDistance: Long,
    val rewardPoints: Long?,
    val participants: List<ParticipantResponse>
) {
    companion object {
        fun from(dto: LeagueResultDto): LeagueResultResponse {
            return LeagueResultResponse(
                previousTier = dto.previousTier.name,
                currentTier = dto.currentTier.name,
                resultStatus = dto.resultStatus.name,
                finalRank = dto.finalRank,
                totalParticipants = dto.totalParticipants,
                totalDistance = dto.totalDistance,
                rewardPoints = dto.rewardPoints,
                participants = dto.participants.map { ParticipantResponse.from(it) }
            )
        }
    }
}
