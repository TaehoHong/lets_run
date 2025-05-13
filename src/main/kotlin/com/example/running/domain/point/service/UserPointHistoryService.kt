package com.example.running.domain.point.service

import com.example.running.domain.point.entity.UserPointHistory
import com.example.running.domain.point.repository.UserPointHistoryRepository
import com.example.running.domain.point.service.dto.PointUsageDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserPointHistoryService(
    private val userPointHistoryRepository: UserPointHistoryRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(pointUsageDto: PointUsageDto) {
        userPointHistoryRepository.save(
            UserPointHistory(
                userId = pointUsageDto.userId,
                point = pointUsageDto.point,
                pointTypeId = pointUsageDto.pointTypeId,
                runningRecordId = pointUsageDto.runningRecordId,
                itemId = pointUsageDto.itemId
            )
        )
    }

    @Transactional(readOnly = true)
    fun findAll(userId: Long, id: Long?, size: Int): List<UserPointHistory> {
        return this.userPointHistoryRepository.findAll(userId, id, size)
    }
}