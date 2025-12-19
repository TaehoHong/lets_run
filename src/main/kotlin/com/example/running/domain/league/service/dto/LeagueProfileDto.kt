package com.example.running.domain.league.service.dto

import com.example.running.domain.league.entity.UserLeagueInfo
import com.example.running.domain.league.enums.RebirthMedal

data class LeagueProfileDto(
    val currentTier: String,
    val rebirthCount: Int,
    val rebirthMedal: String
) {
    companion object {
        fun from(userLeagueInfo: UserLeagueInfo): LeagueProfileDto {
            return LeagueProfileDto(
                currentTier = userLeagueInfo.currentTier.name,
                rebirthCount = userLeagueInfo.rebirthCount,
                rebirthMedal = RebirthMedal.fromRebirthCount(userLeagueInfo.rebirthCount).name
            )
        }
    }
}
