package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.league.repository.LeagueParticipantRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.ceil
import kotlin.math.floor

private val logger = KotlinLogging.logger {}

/**
 * 리그 정산 서비스
 */
@Service
class LeagueSettlementService(
    private val leagueSessionService: LeagueSessionService,
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val userLeagueInfoService: UserLeagueInfoService
) {
    companion object {
        const val PROMOTION_RATE = 0.3   // 상위 30% 승격
        const val RELEGATION_RATE = 0.2  // 하위 20% 강등
    }

    /**
     * 그룹별 정산 처리
     */
    private fun processGroupSettlement(tierType: LeagueTierType, participants: List<LeagueParticipant>) {

        val totalCount = participants.size
        val promotionCut = calculatePromotionCut(totalCount)
        val relegationCut = calculateRelegationCut(totalCount)

        participants.forEachIndexed { index, participant ->
            val rank = index + 1
            val status = determinePromotionStatus(rank, promotionCut, relegationCut, tierType)

            participant.setResult(rank, status)

            // Protected 플래그 설정 (승격/유지/환생인 경우)
            if (status != PromotionStatus.RELEGATED) {
                participant.markProtected()
            }
        }
    }

    /**
     * 즉시 정산 실행 (단순화된 흐름)
     * - 순위 산정 + 승격/강등 결정
     * - 유저에게 즉시 반영
     * - ACTIVE → FINALIZED 직접 전환
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executeImmediateSettlement(sessionId: Long) {
        logger.info { "즉시 정산 시작: 세션 $sessionId" }

        val session = leagueSessionService.getById(sessionId)
        val participants = leagueParticipantRepository.findAllBySessionIdWithUser(sessionId).sortedByDescending { it.totalDistance }

        // 1. 정산 처리 (순위, 승격/강등 결정)
        processGroupSettlement(LeagueTierType.fromId(session.tier.id), participants)

        // 2. 유저에게 즉시 반영
        participants.forEach { participant ->
            participant.user?.let { user ->
                participant.promotionStatus?.let { status ->
                    userLeagueInfoService.applyPromotionStatus(user.id, status)
                    logger.debug { "유저 ${user.id}: $status 적용" }
                }
            }
        }

        // 3. 상태 전환: ACTIVE → FINALIZED
        session.finalize()

        logger.info { "즉시 정산 완료: 세션 $sessionId, ${participants.size}명 처리" }
    }

    // ==================== 헬퍼 메서드 ====================

    /**
     * 승격 컷라인 계산 (상위 30%)
     */
    private fun calculatePromotionCut(totalCount: Int): Int {
        return ceil(totalCount * PROMOTION_RATE).toInt()
    }

    /**
     * 강등 컷라인 계산 (하위 20%의 시작 순위)
     */
    private fun calculateRelegationCut(totalCount: Int): Int {
        return totalCount - floor(totalCount * RELEGATION_RATE).toInt()
    }

    /**
     * 승격/강등 상태 결정
     */
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
}
