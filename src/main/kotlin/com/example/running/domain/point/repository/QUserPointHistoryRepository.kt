package com.example.running.domain.point.repository

import com.example.running.domain.point.entity.UserPointHistory

interface QUserPointHistoryRepository {

    fun findAll(userId: Long, id: Long?, size: Int): List<UserPointHistory>
}