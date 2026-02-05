package com.example.running.domain.achievement.repository

import com.example.running.domain.achievement.entity.UserTitle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTitleRepository : JpaRepository<UserTitle, Long>
