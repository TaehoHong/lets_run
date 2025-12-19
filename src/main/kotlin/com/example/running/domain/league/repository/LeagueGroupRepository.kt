package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LeagueGroupRepository : JpaRepository<LeagueGroup, Long> {

    fun findBySeasonIdAndTierId(seasonId: Long, tierId: Int): List<LeagueGroup>

    @Query("SELECT COUNT(p) FROM LeagueParticipant p WHERE p.group.id = :groupId")
    fun countParticipants(@Param("groupId") groupId: Long): Int
}
