package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSeason
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.SeasonState
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.league.repository.UserLeagueInfoRepository
import com.example.running.domain.league.service.dto.CurrentLeagueDto
import com.example.running.domain.league.service.dto.LeagueHistoryDto
import com.example.running.domain.league.service.dto.LeagueProfileDto
import com.example.running.domain.league.service.dto.LeagueResultDto
import com.example.running.domain.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class LeagueService(
    private val leagueSeasonService: LeagueSeasonService,
    private val leagueGroupService: LeagueGroupService,
    private val leagueParticipantService: LeagueParticipantService,
    private val leagueSettlementService: LeagueSettlementService,
    private val userLeagueInfoService: UserLeagueInfoService,
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val userLeagueInfoRepository: UserLeagueInfoRepository,
    private val userRepository: UserRepository
) {

    /**
     * 현재 리그 정보 조회
     */
    @Transactional(readOnly = true)
    fun getCurrentLeague(userId: Long): CurrentLeagueDto? {
        val participant = leagueParticipantService.getCurrentParticipant(userId)
            ?: return null

        val group = participant.group
        val season = group.season
        val tierType = LeagueTierType.fromId(group.tier.id)

        val rankedParticipants = leagueParticipantService.getRankedParticipants(group.id, userId)
        val totalParticipants = rankedParticipants.size
        val myRank = rankedParticipants.find { it.isMe }?.rank ?: 0

        return CurrentLeagueDto(
            seasonNumber = season.seasonNumber,
            tierName = group.tier.name,
            groupId = group.id,
            myRank = myRank,
            totalParticipants = totalParticipants,
            myDistance = participant.totalDistance,
            promotionCutRank = CurrentLeagueDto.calculatePromotionCutRank(totalParticipants),
            relegationCutRank = CurrentLeagueDto.calculateRelegationCutRank(totalParticipants, tierType),
            remainingDays = leagueSeasonService.calculateRemainingDays(season.endDatetime),
            seasonEndDatetime = season.endDatetime,
            participants = rankedParticipants
        )
    }

    /**
     * 리그 프로필 조회
     */
    @Transactional(readOnly = true)
    fun getLeagueProfile(userId: Long): LeagueProfileDto? {
        return userLeagueInfoService.getLeagueProfile(userId)
    }

    /**
     * 유저를 리그에 참가시킴 (신규 유저 또는 시즌 시작 시)
     * 활성화된 시즌이 없으면 자동으로 새 시즌을 생성
     */
    @Transactional(rollbackFor = [Exception::class])
    fun joinLeague(userId: Long): LeagueParticipant {
        val user = userRepository.findById(userId)
            .orElseThrow { RuntimeException("유저를 찾을 수 없습니다: $userId") }

        // 활성 시즌이 없으면 자동으로 새 시즌 생성
        val season = leagueSeasonService.getCurrentSeason()
            ?: leagueSeasonService.createNewSeason()

        // 이미 참가 중인지 확인
        val existingParticipant = leagueParticipantService.getCurrentParticipant(userId)
        if (existingParticipant != null) {
            return existingParticipant
        }

        // 유저 리그 정보 조회 또는 생성
        val userLeagueInfo = userLeagueInfoService.getOrCreateUserLeagueInfo(user)

        // 적절한 그룹 찾기 또는 생성
        val group = leagueGroupService.getOrCreateGroup(season, userLeagueInfo.currentTier.id)

        // 참가자 추가
        val participant = leagueParticipantService.addParticipant(group, user)

        // 마지막 활동 시즌 업데이트
        userLeagueInfoService.updateLastActiveSeason(userId, season)

        return participant
    }

    /**
     * 러닝 기록 후 거리 업데이트
     *
     * AUDITING 상태(지연 업로드 기간)에서는 순위 재계산 수행
     * - Soft Lock 적용: 기존 승격/유지/환생 유저는 보호
     */
    @Transactional(rollbackFor = [Exception::class])
    fun addRunningDistance(userId: Long, distanceMeters: Long) {
        val participant = leagueParticipantService.getCurrentParticipant(userId)
            ?: run {
                // 참가자가 없으면 자동으로 리그에 참가
                joinLeague(userId)
                leagueParticipantService.getCurrentParticipant(userId)
            }
            ?: throw RuntimeException("리그 참가에 실패했습니다")

        leagueParticipantService.updateDistance(participant.id, distanceMeters)

        // AUDITING 상태에서는 지연 업로드 순위 재계산
        val group = participant.group
        val season = group.season
        if (season.state == SeasonState.AUDITING) {
            val tierType = LeagueTierType.fromId(group.tier.id)
            leagueSettlementService.processLateUploadSettlement(group, tierType)
            logger.info { "지연 업로드 순위 재계산 완료: 유저 $userId, 그룹 ${group.id}" }
        }
    }

    /**
     * 새 시즌 시작
     */
    @Transactional(rollbackFor = [Exception::class])
    fun startNewSeason(): LeagueSeason {
        // 기존 시즌 종료 처리
        processSeasonEnd()

        // 새 시즌 생성
        val newSeason = leagueSeasonService.createNewSeason()

        // 모든 활성 유저를 새 시즌에 배정
        assignUsersToNewSeason(newSeason)

        // 봇 보충
        fillBotsForSeason(newSeason)

        return newSeason
    }

    /**
     * 시즌 종료 처리
     */
    @Transactional(rollbackFor = [Exception::class])
    fun processSeasonEnd() {
        val currentSeason = leagueSeasonService.getCurrentSeason() ?: return

        // 각 티어별로 처리
        LeagueTierType.entries.forEach { tierType ->
            val groups = leagueGroupService.getGroupsBySeasonAndTier(currentSeason.id, tierType.id)

            groups.forEach { group ->
                val participants = leagueParticipantService.processSeasonEnd(group.id, tierType)

                // 실제 유저의 승격/강등 상태 반영
                participants
                    .filter { !it.isBot && it.user != null }
                    .forEach { participant ->
                        participant.promotionStatus?.let { status ->
                            userLeagueInfoService.applyPromotionStatus(participant.user!!.id, status)
                        }
                    }
            }
        }

        // 시즌 비활성화
        leagueSeasonService.endCurrentSeason()
    }

    /**
     * 새 시즌에 유저 배정
     */
    private fun assignUsersToNewSeason(season: LeagueSeason) {
        val activeUsers = userLeagueInfoService.getAllActiveUsers()

        activeUsers.forEach { userLeagueInfo ->
            val group = leagueGroupService.getOrCreateGroup(season, userLeagueInfo.currentTier.id)
            leagueParticipantService.addParticipant(group, userLeagueInfo.user)
            userLeagueInfoService.updateLastActiveSeason(userLeagueInfo.user.id, season)
        }
    }

    /**
     * 시즌의 모든 그룹에 봇 보충
     */
    private fun fillBotsForSeason(season: LeagueSeason) {
        LeagueTierType.entries.forEach { tierType ->
            val groups = leagueGroupService.getGroupsBySeasonAndTier(season.id, tierType.id)
            val averageDistance = leagueParticipantService.getAverageDistance(season.id, tierType.id)

            groups.forEach { group ->
                fillBotsForGroup(group.id, averageDistance)
            }
        }
    }

    /**
     * 그룹에 봇 보충 (최소 인원 미달 시)
     */
    private fun fillBotsForGroup(groupId: Long, averageDistance: Long) {
        val group = leagueGroupService.getGroupById(groupId) ?: return
        val currentCount = leagueParticipantService.countParticipants(groupId)

        val botsNeeded = LeagueGroupService.MIN_PARTICIPANTS - currentCount
        if (botsNeeded <= 0) return

        repeat(botsNeeded) {
            leagueParticipantService.addBot(group, averageDistance)
        }
    }

    /**
     * 미확인 리그 결과 조회
     */
    @Transactional(readOnly = true)
    fun getUncheckedResult(userId: Long): LeagueResultDto? {
        val participant = leagueParticipantRepository.findUncheckedResultByUserId(userId)
            ?: return null

        val group = participant.group
        val season = group.season
        val currentTier = LeagueTierType.fromId(group.tier.id)
        val resultStatus = participant.promotionStatus ?: return null

        // 그룹 내 전체 참가자 수
        val totalParticipants = leagueParticipantService.countParticipants(group.id)

        // 이전 티어 계산
        val previousTier = LeagueResultDto.calculatePreviousTier(currentTier, resultStatus)

        // 보상 포인트 계산 (승격/환생 시에만)
        val rewardPoints = LeagueResultDto.calculateRewardPoints(resultStatus, currentTier)

        return LeagueResultDto(
            seasonNumber = season.seasonNumber,
            previousTier = previousTier,
            currentTier = currentTier,
            resultStatus = resultStatus,
            finalRank = participant.finalRank ?: 0,
            totalParticipants = totalParticipants,
            totalDistance = participant.totalDistance,
            rewardPoints = rewardPoints
        )
    }

    /**
     * 리그 결과 확인 처리
     */
    @Transactional(rollbackFor = [Exception::class])
    fun confirmResult(userId: Long) {
        val participant = leagueParticipantRepository.findUncheckedResultByUserId(userId)
            ?: return

        participant.markResultChecked()
        leagueParticipantRepository.save(participant)
    }

    /**
     * 리그 히스토리 조회 (종료된 시즌)
     */
    @Transactional(readOnly = true)
    fun getHistory(userId: Long, cursor: Long?, size: Int): Pair<List<LeagueHistoryDto>, List<Long>> {
        val participants = leagueParticipantRepository.findHistoryByUserId(userId, cursor, size)

        val histories = participants.map { participant ->
            val totalParticipants = leagueParticipantService.countParticipants(participant.group.id)
            LeagueHistoryDto.from(participant, totalParticipants)
        }

        val participantIds = participants.map { it.id }

        return Pair(histories, participantIds)
    }

    // ==================== 장기 미접속 유저 처리 ====================

    /**
     * 장기 미접속 유저 처리
     *
     * 노션 기획서 정책:
     * - 2시즌 이상 미참여 시 비활성화 처리
     * - 비활성화 시 1단계 강등 (최하위 티어는 유지)
     * - 복귀 시 재활성화 가능
     */
    @Transactional(rollbackFor = [Exception::class])
    fun processInactiveUsers() {
        logger.info { "장기 미접속 유저 처리 시작" }

        val currentSeason = leagueSeasonService.getCurrentSeason()
        if (currentSeason == null) {
            logger.warn { "활성 시즌이 없어 장기 미접속 유저 처리를 건너뜁니다" }
            return
        }

        // 2시즌 이상 미참여 유저 조회
        val inactiveUsers = userLeagueInfoRepository.findInactiveUsers(currentSeason.seasonNumber)

        if (inactiveUsers.isEmpty()) {
            logger.info { "처리할 장기 미접속 유저가 없습니다" }
            return
        }

        var processedCount = 0
        inactiveUsers.forEach { userLeagueInfo ->
            try {
                userLeagueInfoService.handleInactiveUser(userLeagueInfo.user.id)
                processedCount++
                logger.debug { "유저 ${userLeagueInfo.user.id} 비활성화 처리 완료" }
            } catch (e: Exception) {
                logger.error(e) { "유저 ${userLeagueInfo.user.id} 비활성화 처리 실패" }
            }
        }

        logger.info { "장기 미접속 유저 처리 완료: ${processedCount}/${inactiveUsers.size}명 처리" }
    }
}
