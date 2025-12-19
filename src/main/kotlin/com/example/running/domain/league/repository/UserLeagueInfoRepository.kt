package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.UserLeagueInfo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserLeagueInfoRepository : JpaRepository<UserLeagueInfo, Long> {

    fun findByUserId(userId: Long): UserLeagueInfo?

    fun findByUserIdAndIsActiveTrue(userId: Long): UserLeagueInfo?

    @Query("SELECT u FROM UserLeagueInfo u WHERE u.isActive = true AND u.currentTier.id = :tierId")
    fun findActiveUsersByTierId(@Param("tierId") tierId: Int): List<UserLeagueInfo>

    @Query("SELECT u FROM UserLeagueInfo u WHERE u.isActive = true")
    fun findAllActiveUsers(): List<UserLeagueInfo>

    @Query("""
        SELECT u FROM UserLeagueInfo u
        WHERE u.isActive = true
        AND (u.lastActiveSeason IS NULL OR u.lastActiveSeason.seasonNumber < :currentSeasonNumber - 1)
    """)
    fun findInactiveUsers(@Param("currentSeasonNumber") currentSeasonNumber: Int): List<UserLeagueInfo>
}
