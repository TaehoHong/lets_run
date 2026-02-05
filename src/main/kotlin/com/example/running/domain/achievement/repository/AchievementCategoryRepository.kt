package com.example.running.domain.achievement.repository

import com.example.running.domain.achievement.entity.AchievementCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AchievementCategoryRepository : JpaRepository<AchievementCategory, Short>
