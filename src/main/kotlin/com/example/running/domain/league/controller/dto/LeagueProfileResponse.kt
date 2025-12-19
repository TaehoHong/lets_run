package com.example.running.domain.league.controller.dto

import com.example.running.domain.league.service.dto.LeagueProfileDto

data class LeagueProfileResponse(
    val currentTier: String,
    val rebirthCount: Int,
    val rebirthMedal: String
) {
    companion object {
        fun from(dto: LeagueProfileDto): LeagueProfileResponse {
            return LeagueProfileResponse(
                currentTier = dto.currentTier,
                rebirthCount = dto.rebirthCount,
                rebirthMedal = dto.rebirthMedal
            )
        }
    }
}
