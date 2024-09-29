package com.example.running.domain.point.repository

import com.example.running.domain.point.entity.UserPoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserPointRepository: JpaRepository<UserPoint, Long>