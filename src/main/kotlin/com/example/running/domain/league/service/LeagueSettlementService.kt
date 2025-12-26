package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueGroup
import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSeason
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
    private val leagueSeasonService: LeagueSeasonService,
    private val leagueGroupService: LeagueGroupService,
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val userLeagueInfoService: UserLeagueInfoService
) {
    companion object {
        const val PROMOTION_RATE = 0.3   // 상위 30% 승격
        const val RELEGATION_RATE = 0.2  // 하위 20% 강등
    }

    // ==================== 1차 정산 (월요일 00:15) ====================

    /**
     * 1차 정산 실행
     * - 각 그룹별 순위 산정
     * - 승격/강등/환생 상태 설정
     * - Protected 플래그 설정 (Soft Lock)
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executePrimarySettlement(season: LeagueSeason) {
        logger.info { "1차 정산 시작: 시즌 ${season.seasonNumber}" }

        // 상태 전환: LOCKED -> CALCULATING
        leagueSeasonService.startCalculating(season.id)

        LeagueTierType.entries.forEach { tierType ->
            val groups = leagueGroupService.getGroupsBySeasonAndTier(season.id, tierType.id)

            groups.forEach { group ->
                processGroupSettlement(group, tierType)
            }
        }

        // 상태 전환: CALCULATING -> AUDITING
        leagueSeasonService.startAuditing(season.id)

        logger.info { "1차 정산 완료: 시즌 ${season.seasonNumber}" }
    }

    /**
     * 그룹별 정산 처리
     */
    private fun processGroupSettlement(group: LeagueGroup, tierType: LeagueTierType) {
        val participants = leagueParticipantRepository
            .findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(group.id)

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

        logger.info { "그룹 ${group.id} (${tierType}) 정산: 총 ${totalCount}명, 승격컷 ${promotionCut}위, 강등컷 ${relegationCut}위" }
    }

    // ==================== 지연 업로드 처리 (AUDITING) ====================

    /**
     * 지연 업로드 후 순위 재계산
     * - Protected 유저는 보호 (기존 결과 유지)
     * - 새 기록으로 인한 순위 상승은 허용
     */
    @Transactional(rollbackFor = [Exception::class])
    fun processLateUploadSettlement(group: LeagueGroup, tierType: LeagueTierType) {
        val participants = leagueParticipantRepository
            .findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(group.id)

        val totalCount = participants.size
        val promotionCut = calculatePromotionCut(totalCount)
        val relegationCut = calculateRelegationCut(totalCount)

        participants.forEachIndexed { index, participant ->
            val newRank = index + 1
            val newStatus = determinePromotionStatus(newRank, promotionCut, relegationCut, tierType)

            // Soft Lock 적용
            val finalStatus = applySoftLock(participant, newStatus)

            participant.finalRank = newRank
            participant.promotionStatus = finalStatus
        }
    }

    /**
     * Soft Lock 적용
     * - Protected 유저는 기존 상태보다 나빠지지 않음
     * - 지연 업로더는 보호 없이 새 순위 적용
     */
    private fun applySoftLock(
        participant: LeagueParticipant,
        newStatus: PromotionStatus
    ): PromotionStatus {
        // Protected가 아니면 새 상태 그대로 적용
        if (!participant.isProtected) {
            return newStatus
        }

        val originalStatus = participant.promotionStatus ?: return newStatus

        // 기존 상태가 새 상태보다 좋으면 기존 상태 유지
        return if (isStatusBetter(originalStatus, newStatus)) {
            logger.debug { "Soft Lock 적용: 참가자 ${participant.id}, ${originalStatus} 유지 (새 상태: $newStatus)" }
            originalStatus
        } else {
            // 새 상태가 더 좋거나 같으면 새 상태 적용
            newStatus
        }
    }

    /**
     * 상태 우선순위 비교 (REBIRTH > PROMOTED > MAINTAINED > RELEGATED)
     */
    private fun isStatusBetter(status1: PromotionStatus, status2: PromotionStatus): Boolean {
        val priority = mapOf(
            PromotionStatus.REBIRTH to 4,
            PromotionStatus.PROMOTED to 3,
            PromotionStatus.MAINTAINED to 2,
            PromotionStatus.RELEGATED to 1
        )
        return (priority[status1] ?: 0) > (priority[status2] ?: 0)
    }

    // ==================== 최종 확정 (화요일 00:00) ====================

    /**
     * 최종 확정 실행
     * - 결과 유저에게 반영
     * - 시즌 FINALIZED로 상태 전환
     */
    @Transactional(rollbackFor = [Exception::class])
    fun executeFinalSettlement(season: LeagueSeason) {
        logger.info { "최종 확정 시작: 시즌 ${season.seasonNumber}" }

        // 실제 유저에게 승격/강등 반영
        val participants = leagueParticipantRepository.findParticipantsWithResultBySeasonId(season.id)

        participants.forEach { participant ->
            participant.user?.let { user ->
                participant.promotionStatus?.let { status ->
                    userLeagueInfoService.applyPromotionStatus(user.id, status)
                    logger.debug { "유저 ${user.id}: ${status} 적용" }
                }
            }
        }

        // 상태 전환: AUDITING -> FINALIZED
        leagueSeasonService.finalizeSeason(season.id)

        logger.info { "최종 확정 완료: 시즌 ${season.seasonNumber}, ${participants.size}명 처리" }
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
