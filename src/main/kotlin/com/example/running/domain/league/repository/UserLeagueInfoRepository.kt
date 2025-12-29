package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.UserLeagueInfo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserLeagueInfoRepository : JpaRepository<UserLeagueInfo, Long> {

    fun findByUserId(userId: Long): UserLeagueInfo?
}
