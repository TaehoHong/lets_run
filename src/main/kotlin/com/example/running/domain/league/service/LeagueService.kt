package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.repository.LeagueParticipantRepository
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
    private val leagueSessionService: LeagueSessionService,
    private val leagueParticipantService: LeagueParticipantService,
    private val userLeagueInfoService: UserLeagueInfoService,
    private val leagueParticipantRepository: LeagueParticipantRepository,
    private val userRepository: UserRepository
) {

    /**
     * 현재 리그 정보 조회
     */
    @Transactional(readOnly = true)
    fun getCurrentLeague(userId: Long): CurrentLeagueDto? {
        val participant = leagueParticipantService.getCurrentParticipant(userId)
            ?: return null

        val session = participant.leagueSession
        val tierType = LeagueTierType.fromId(session.tier.id)

        val rankedParticipants = leagueParticipantService.getRankedParticipants(session.id, userId)
        val totalParticipants = rankedParticipants.size
        val myRank = rankedParticipants.find { it.isMe }?.rank ?: 0

        return CurrentLeagueDto(
            tierName = session.tier.name,
            sessionId = session.id,
            myRank = myRank,
            totalParticipants = totalParticipants,
            myDistance = participant.totalDistance,
            promotionCutRank = CurrentLeagueDto.calculatePromotionCutRank(totalParticipants),
            relegationCutRank = CurrentLeagueDto.calculateRelegationCutRank(totalParticipants, tierType),
            remainingDays = leagueSessionService.calculateRemainingDays(session.endDatetime),
            seasonEndDatetime = session.endDatetime,
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
     * 활성화된 세션이 없으면 자동으로 새 시즌을 생성
     */
    @Transactional(rollbackFor = [Exception::class])
    fun joinLeague(userId: Long): LeagueParticipant {
        val user = userRepository.findById(userId)
            .orElseThrow { RuntimeException("유저를 찾을 수 없습니다: $userId") }

        // 활성 세션이 없으면 자동으로 새 시즌 생성
        val session = leagueSessionService.getCurrentSeason()
            ?: leagueSessionService.createNewSeason()

        // 이미 참가 중인지 확인
        val existingParticipant = leagueParticipantService.getCurrentParticipant(userId)
        if (existingParticipant != null) {
            return existingParticipant
        }

        // 유저 리그 정보 조회 또는 생성
        userLeagueInfoService.getOrCreateUserLeagueInfo(user)

        // 참가자 추가
        val participant = leagueParticipantService.addParticipant(session, user)

        return participant
    }

    /**
     * 미확인 리그 결과 조회
     */
    @Transactional(readOnly = true)
    fun getUncheckedResult(userId: Long): LeagueResultDto? {
        val participant = leagueParticipantRepository.findUncheckedResultByUserId(userId)
            ?: return null

        val session = participant.leagueSession
        val currentTier = LeagueTierType.fromId(session.tier.id)
        val resultStatus = participant.promotionStatus ?: return null

        // 그룹 내 전체 참가자 수
        val totalParticipants = leagueParticipantService.countParticipants(session.id)

        // 이전 티어 계산
        val previousTier = LeagueResultDto.calculatePreviousTier(currentTier, resultStatus)

        // 보상 포인트 계산 (승격/환생 시에만)
        val rewardPoints = LeagueResultDto.calculateRewardPoints(resultStatus, currentTier)

        return LeagueResultDto(
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
            val totalParticipants = leagueParticipantService.countParticipants(participant.leagueSession.id)
            LeagueHistoryDto.from(participant, totalParticipants)
        }

        val participantIds = participants.map { it.id }

        return Pair(histories, participantIds)
    }
}
