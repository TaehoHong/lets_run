package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.entity.Avatar
import org.springframework.data.jpa.repository.JpaRepository

interface AvatarRepository: JpaRepository<Avatar, Long> {

    fun findByUserIdAndIsMain(userId: Long, isMain: Boolean): Avatar?

    fun existsByUserIdAndId(userId: Long, id: Long): Boolean
}