package com.example.running.domain.league.controller.dto

import com.example.running.domain.league.service.dto.CurrentLeagueDto
import com.example.running.domain.league.service.dto.LeagueParticipantDto
import java.time.OffsetDateTime

data class CurrentLeagueResponse(
    val seasonNumber: Int,
    val tierName: String,
    val groupId: Long,
    val myRank: Int,
    val totalParticipants: Int,
    val myDistance: Long,
    val promotionCutRank: Int,
    val relegationCutRank: Int,
    val remainingDays: Long,
    val seasonEndDatetime: OffsetDateTime,
    val participants: List<ParticipantResponse>
) {
    companion object {
        fun from(dto: CurrentLeagueDto): CurrentLeagueResponse {
            return CurrentLeagueResponse(
                seasonNumber = dto.seasonNumber,
                tierName = dto.tierName,
                groupId = dto.groupId,
                myRank = dto.myRank,
                totalParticipants = dto.totalParticipants,
                myDistance = dto.myDistance,
                promotionCutRank = dto.promotionCutRank,
                relegationCutRank = dto.relegationCutRank,
                remainingDays = dto.remainingDays,
                seasonEndDatetime = dto.seasonEndDatetime,
                participants = dto.participants.map { ParticipantResponse.from(it) }
            )
        }
    }
}

data class ParticipantResponse(
    val rank: Int,
    val nickname: String?,
    val distance: Long,
    val isMe: Boolean,
    val isBot: Boolean
) {
    companion object {
        fun from(dto: LeagueParticipantDto): ParticipantResponse {
            return ParticipantResponse(
                rank = dto.rank,
                nickname = dto.nickname,
                distance = dto.totalDistance,
                isMe = dto.isMe,
                isBot = dto.isBot
            )
        }
    }
}
