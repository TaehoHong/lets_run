package com.example.running.domain.point.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.point.entity.UserPointHistory
import com.example.running.domain.point.repository.UserPointHistoryRepository
import com.example.running.domain.point.service.dto.PointUsageDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

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
    fun search(userId: Long, isEarned: Boolean?, cursor: Long?,size: Int, startCreatedDatetime: OffsetDateTime?): CursorResult<UserPointHistory> {
        val content = userPointHistoryRepository.findAll(userId, cursor, isEarned, startCreatedDatetime, size)
        val hasNext = userPointHistoryRepository.existsBy(userId, cursor, isEarned, startCreatedDatetime)
        return CursorResult(content, content.lastOrNull()?.id, hasNext)
    }
}