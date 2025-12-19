package com.example.running.domain.league.service

import com.example.running.domain.league.entity.LeagueGroup
import com.example.running.domain.league.entity.LeagueSeason
import com.example.running.domain.league.entity.LeagueTier
import com.example.running.domain.league.repository.LeagueGroupRepository
import com.example.running.domain.league.repository.LeagueTierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeagueGroupService(
    private val leagueGroupRepository: LeagueGroupRepository,
    private val leagueTierRepository: LeagueTierRepository
) {
    companion object {
        const val MIN_PARTICIPANTS = 20
        const val MAX_PARTICIPANTS = 40
    }

    @Transactional(rollbackFor = [Exception::class])
    fun createGroup(season: LeagueSeason, tier: LeagueTier): LeagueGroup {
        val group = LeagueGroup(
            season = season,
            tier = tier
        )

        return leagueGroupRepository.save(group)
    }

    @Transactional(readOnly = true)
    fun getGroupsBySeasonAndTier(seasonId: Long, tierId: Int): List<LeagueGroup> {
        return leagueGroupRepository.findBySeasonIdAndTierId(seasonId, tierId)
    }

    @Transactional(readOnly = true)
    fun getGroupById(groupId: Long): LeagueGroup? {
        return leagueGroupRepository.findById(groupId).orElse(null)
    }

    @Transactional(readOnly = true)
    fun countParticipants(groupId: Long): Int {
        return leagueGroupRepository.countParticipants(groupId)
    }

    @Transactional(readOnly = true)
    fun findAvailableGroup(seasonId: Long, tierId: Int): LeagueGroup? {
        val groups = getGroupsBySeasonAndTier(seasonId, tierId)

        // 최대 인원 미만인 그룹 찾기
        for (group in groups) {
            val count = countParticipants(group.id)
            if (count < MAX_PARTICIPANTS) {
                return group
            }
        }

        return null
    }

    @Transactional(rollbackFor = [Exception::class])
    fun getOrCreateGroup(season: LeagueSeason, tierId: Int): LeagueGroup {
        val tier = leagueTierRepository.findById(tierId)
            .orElseThrow { RuntimeException("존재하지 않는 티어입니다: $tierId") }

        return findAvailableGroup(season.id, tierId)
            ?: createGroup(season, tier)
    }
}
