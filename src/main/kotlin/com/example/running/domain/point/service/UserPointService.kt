package com.example.running.domain.point.service

import com.example.running.domain.point.entity.UserPoint
import com.example.running.domain.point.repository.UserPointRepository
import com.example.running.domain.point.service.dto.PointUsageDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserPointService(
    private val userPointHistoryService: UserPointHistoryService,
    private val userPointRepository: UserPointRepository
) {


    @Transactional(rollbackFor = [Exception::class])
    fun updatePoint(pointUsageDto: PointUsageDto) {
        getByUserId(pointUsageDto.userId).apply {
            this.point += pointUsageDto.point
        }.let {
            userPointRepository.save(it)
        }.also {
            userPointHistoryService.save(pointUsageDto)
        }
    }


    private fun getByUserId(userId: Long): UserPoint {
        return userPointRepository.findById(userId)
            .orElseThrow { RuntimeException("Point not Found") }
    }

}