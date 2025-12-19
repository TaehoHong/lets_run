package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueTier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LeagueTierRepository : JpaRepository<LeagueTier, Int>
