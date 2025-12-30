package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.repository.LeagueParticipantRepository
import com.example.running.domain.league.service.dto.LeagueParticipantDto
import com.example.running.domain.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeagueParticipantService(
    private val leagueParticipantRepository: LeagueParticipantRepository,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun addParticipant(session: LeagueSession, user: User): LeagueParticipant {
        val participant = LeagueParticipant.createParticipant(session, user)
        return leagueParticipantRepository.save(participant)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updateDistance(participantId: Long, distance: Long) {
        val participant = leagueParticipantRepository.findById(participantId)
            .orElseThrow { RuntimeException("참가자를 찾을 수 없습니다: $participantId") }
        participant.addDistance(distance)
    }

    @Transactional(readOnly = true)
    fun getCurrentParticipant(userId: Long): LeagueParticipant? {
        return leagueParticipantRepository.findCurrentParticipantByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun getRankedParticipants(sessionId: Long, currentUserId: Long? = null): List<LeagueParticipantDto> {
        val participants = leagueParticipantRepository
            .findByLeagueSessionIdOrderByTotalDistanceDescDistanceAchievedAtAsc(sessionId)

        return participants.mapIndexed { index, participant ->
            LeagueParticipantDto.from(participant, index + 1, currentUserId)
        }
    }

    @Transactional(readOnly = true)
    fun countParticipants(sessionId: Long): Int {
        return leagueParticipantRepository.countBySessionId(sessionId)
    }

}
