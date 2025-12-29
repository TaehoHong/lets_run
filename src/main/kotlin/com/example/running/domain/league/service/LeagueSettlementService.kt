package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueSession
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
 *
 * 노션 기획서 정산 정책:
 * - 일요일 23:59:59: 시즌 종료 (LOCKED)
 * - 월요일 00:15: 1차 정산, Protected 플래그 설정
 * - 월요일 00:15 ~ 화요일 00:00: 지연 업로드 처리 (Soft Lock)
 * - 화요일 00:00: 최종 확정
 *
 * Soft Lock 핵심 원칙: 1차 정산 결과보다 나빠지는 변동은 없음
 *
 * @see <a href="https://www.notion.so/2cc405e9dd388175bf1cf008612a3876">리그 기획서</a>
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

    // ==================== 1차 정산 (월요일 00:15) ====================

    /**
     * 정산 실행
     * - 각 그룹별 순위 산정
     * - 승격/강등/환생 상태 설정
     * - Protected 플래그 설정 (Soft Lock)
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executePrimarySettlement(seasonId: Long) {
        logger.info { "정산 시작: 세션 $seasonId" }

        // 상태 전환: LOCKED -> CALCULATING
        val leagueSession = leagueSessionService.startCalculating(seasonId)

        processGroupSettlement(leagueSession)

        // 상태 전환: CALCULATING -> AUDITING
        leagueSessionService.startAuditing(seasonId)

        logger.info { "정산 완료: 세션 $seasonId" }
    }

    /**
     * 그룹별 정산 처리
     */
    private fun processGroupSettlement(session: LeagueSession) {
        val participants = leagueParticipantRepository.findByLeagueSessionIdOrderByTotalDistanceDescDistanceAchievedAtAsc(session.id)

        val totalCount = participants.size
        val promotionCut = calculatePromotionCut(totalCount)
        val relegationCut = calculateRelegationCut(totalCount)

        participants.forEachIndexed { index, participant ->
            val rank = index + 1
            val status = determinePromotionStatus(rank, promotionCut, relegationCut, LeagueTierType.fromId(session.tier.id))

            participant.setResult(rank, status)

            // Protected 플래그 설정 (승격/유지/환생인 경우)
            if (status != PromotionStatus.RELEGATED) {
                participant.markProtected()
            }
        }
    }

    // ==================== 최종 확정 (화요일 00:00) ====================

    /**
     * 최종 확정 실행
     * - 결과 유저에게 반영
     * - 시즌 FINALIZED로 상태 전환
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executeFinalSettlement(session: LeagueSession) {
        logger.info { "최종 확정 시작: 시즌 ${session.id}" }

        // 실제 유저에게 승격/강등 반영
        val participants = leagueParticipantRepository.findParticipantsWithResultBySeasonId(session.id)

        participants.forEach { participant ->
            participant.user?.let { user ->
                participant.promotionStatus?.let { status ->
                    userLeagueInfoService.applyPromotionStatus(user.id, status)
                    logger.debug { "유저 ${user.id}: ${status} 적용" }
                }
            }
        }

        // 상태 전환: AUDITING -> FINALIZED
        leagueSessionService.finalizeSeason(session.id)

        logger.info { "최종 확정 완료: 시즌 ${session.id}, ${participants.size}명 처리" }
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
