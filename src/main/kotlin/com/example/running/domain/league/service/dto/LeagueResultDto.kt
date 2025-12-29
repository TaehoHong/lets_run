package com.example.running.domain.league.service.dto

import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.PromotionStatus

/**
 * 리그 시즌 결과 DTO
 */
data class LeagueResultDto(
    val previousTier: LeagueTierType,
    val currentTier: LeagueTierType,
    val resultStatus: PromotionStatus,
    val finalRank: Int,
    val totalParticipants: Int,
    val totalDistance: Long,
    val rewardPoints: Long?
) {
    companion object {
        /**
         * 승격 보상 포인트 계산
         */
        fun calculateRewardPoints(status: PromotionStatus, tier: LeagueTierType): Long? {
            if (status != PromotionStatus.PROMOTED && status != PromotionStatus.REBIRTH) {
                return null
            }

            // 티어별 승격 보상 포인트
            return when (tier) {
                LeagueTierType.SILVER -> 100L
                LeagueTierType.GOLD -> 200L
                LeagueTierType.PLATINUM -> 300L
                LeagueTierType.DIAMOND -> 500L
                LeagueTierType.CHALLENGER -> 1000L
                LeagueTierType.BRONZE -> null // 브론즈는 최하위이므로 승격 불가
            }
        }

        /**
         * 이전 티어 계산 (결과 상태 기반)
         */
        fun calculatePreviousTier(currentTier: LeagueTierType, status: PromotionStatus): LeagueTierType {
            return when (status) {
                PromotionStatus.PROMOTED -> {
                    // 승격했으면 이전 티어는 한 단계 아래
                    LeagueTierType.getPreviousTier(currentTier) ?: currentTier
                }
                PromotionStatus.RELEGATED -> {
                    // 강등됐으면 이전 티어는 한 단계 위
                    LeagueTierType.getNextTier(currentTier) ?: currentTier
                }
                PromotionStatus.REBIRTH -> {
                    // 환생이면 이전 티어는 CHALLENGER
                    LeagueTierType.CHALLENGER
                }
                PromotionStatus.MAINTAINED -> {
                    // 유지면 동일
                    currentTier
                }
            }
        }
    }
}
