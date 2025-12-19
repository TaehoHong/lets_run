package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueSeason
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface LeagueSeasonRepository : JpaRepository<LeagueSeason, Long> {

    fun findByIsActiveTrue(): LeagueSeason?

    fun findTopByOrderBySeasonNumberDesc(): LeagueSeason?

    @Query("SELECT MAX(s.seasonNumber) FROM LeagueSeason s")
    fun findMaxSeasonNumber(): Int?
}
