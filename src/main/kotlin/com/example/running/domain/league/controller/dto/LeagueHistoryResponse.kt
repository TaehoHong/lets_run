package com.example.running.domain.league.controller.dto

import com.example.running.domain.league.service.dto.LeagueHistoryDto

/**
 * 리그 히스토리 목록 응답 DTO
 */
data class LeagueHistoryResponse(
    val histories: List<LeagueHistoryItemResponse>,
    val nextCursor: Long?,
    val hasMore: Boolean
) {
    companion object {
        fun from(
            histories: List<LeagueHistoryDto>,
            participantIds: List<Long>,
            size: Int
        ): LeagueHistoryResponse {
            val hasMore = histories.size > size
            val items = histories.take(size)
            val ids = participantIds.take(size)

            return LeagueHistoryResponse(
                histories = items.mapIndexed { index, dto ->
                    LeagueHistoryItemResponse.from(dto, ids.getOrNull(index))
                },
                nextCursor = if (hasMore) ids.lastOrNull() else null,
                hasMore = hasMore
            )
        }
    }
}

/**
 * 리그 히스토리 항목 응답 DTO
 */
data class LeagueHistoryItemResponse(
    val id: Long?,
    val seasonNumber: Int,
    val tier: String,
    val rank: Int?,
    val totalParticipants: Int,
    val totalDistance: Long,
    val promotionStatus: String?
) {
    companion object {
        fun from(dto: LeagueHistoryDto, participantId: Long?): LeagueHistoryItemResponse {
            return LeagueHistoryItemResponse(
                id = participantId,
                seasonNumber = dto.seasonNumber,
                tier = dto.tier,
                rank = dto.rank,
                totalParticipants = dto.totalParticipants,
                totalDistance = dto.totalDistance,
                promotionStatus = dto.promotionStatus
            )
        }
    }
}
