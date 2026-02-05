package com.example.running.domain.achievement.repository

import com.example.running.domain.achievement.entity.Title
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TitleRepository : JpaRepository<Title, Long>
