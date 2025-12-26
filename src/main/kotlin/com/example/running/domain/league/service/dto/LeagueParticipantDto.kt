package com.example.running.domain.league.service.dto

import com.example.running.domain.league.entity.LeagueParticipant

data class LeagueParticipantDto(
    val id: Long,
    val userId: Long?,
    val nickname: String?,
    val totalDistance: Long,
    val rank: Int,
    val isBot: Boolean,
    val isMe: Boolean = false
) {
    companion object {
        fun from(participant: LeagueParticipant, rank: Int, currentUserId: Long? = null): LeagueParticipantDto {
            return LeagueParticipantDto(
                id = participant.id,
                userId = participant.user?.id,
                nickname = if (participant.isBot) participant.botName else participant.user?.nickname,
                totalDistance = participant.totalDistance,
                rank = rank,
                isBot = participant.isBot,
                isMe = currentUserId != null && participant.user?.id == currentUserId
            )
        }
    }
}
