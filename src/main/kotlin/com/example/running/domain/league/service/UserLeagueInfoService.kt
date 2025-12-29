package com.example.running.domain.league.service

import com.example.running.domain.league.entity.UserLeagueInfo
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.league.repository.LeagueTierRepository
import com.example.running.domain.league.repository.UserLeagueInfoRepository
import com.example.running.domain.league.service.dto.LeagueProfileDto
import com.example.running.domain.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserLeagueInfoService(
    private val userLeagueInfoRepository: UserLeagueInfoRepository,
    private val leagueTierRepository: LeagueTierRepository
) {

    @Transactional(readOnly = true)
    fun getUserLeagueInfo(userId: Long): UserLeagueInfo? {
        return userLeagueInfoRepository.findByUserId(userId)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun createUserLeagueInfo(user: User): UserLeagueInfo {
        val existingInfo = userLeagueInfoRepository.findByUserId(user.id)
        if (existingInfo != null) {
            return existingInfo
        }

        val bronzeTier = leagueTierRepository.findById(LeagueTierType.BRONZE.id)
            .orElseThrow { RuntimeException("브론즈 티어를 찾을 수 없습니다") }

        val userLeagueInfo = UserLeagueInfo(
            user = user,
            currentTier = bronzeTier
        )

        return userLeagueInfoRepository.save(userLeagueInfo)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun getOrCreateUserLeagueInfo(user: User): UserLeagueInfo {
        return getUserLeagueInfo(user.id) ?: createUserLeagueInfo(user)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun applyPromotionStatus(userId: Long, status: PromotionStatus) {
        val userLeagueInfo = userLeagueInfoRepository.findByUserId(userId)
            ?: throw RuntimeException("유저 리그 정보를 찾을 수 없습니다: $userId")

        when (status) {
            PromotionStatus.PROMOTED -> promote(userLeagueInfo)
            PromotionStatus.RELEGATED -> relegate(userLeagueInfo)
            PromotionStatus.REBIRTH -> rebirth(userLeagueInfo)
            PromotionStatus.MAINTAINED -> { /* 변경 없음 */
            }
        }
    }

    @Transactional(readOnly = true)
    fun getLeagueProfile(userId: Long): LeagueProfileDto? {
        val userLeagueInfo = userLeagueInfoRepository.findByUserId(userId)
            ?: return null

        return LeagueProfileDto.from(userLeagueInfo)
    }

    private fun promote(userLeagueInfo: UserLeagueInfo) {
        val currentTierType = LeagueTierType.fromId(userLeagueInfo.currentTier.id)
        val nextTier = LeagueTierType.getNextTier(currentTierType) ?: return

        val tier = leagueTierRepository.findById(nextTier.id)
            .orElseThrow { RuntimeException("티어를 찾을 수 없습니다: ${nextTier.id}") }
        userLeagueInfo.currentTier = tier
    }

    private fun relegate(userLeagueInfo: UserLeagueInfo) {
        val currentTierType = LeagueTierType.fromId(userLeagueInfo.currentTier.id)
        val previousTier = LeagueTierType.getPreviousTier(currentTierType) ?: return

        val tier = leagueTierRepository.findById(previousTier.id)
            .orElseThrow { RuntimeException("티어를 찾을 수 없습니다: ${previousTier.id}") }
        userLeagueInfo.currentTier = tier
    }

    private fun rebirth(userLeagueInfo: UserLeagueInfo) {
        userLeagueInfo.rebirthCount++

        // 환생 시 골드 리그에서 재시작
        val goldTier = leagueTierRepository.findById(LeagueTierType.GOLD.id)
            .orElseThrow { RuntimeException("골드 티어를 찾을 수 없습니다") }
        userLeagueInfo.currentTier = goldTier
    }
}
