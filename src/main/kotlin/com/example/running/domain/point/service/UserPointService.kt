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

    @Transactional
    fun save(userId: Long) {
        userPointRepository.save(
            UserPoint(userId = userId)
        )
    }

    fun verifyPoint(userId: Long, point: Int) {
        getOrCreateByUserId(userId).apply {
            if (this.point < point) {
                throw RuntimeException("포인트가 부족합니다.")
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updatePoint(pointUsageDto: PointUsageDto) {
        getOrCreateByUserId(pointUsageDto.userId).apply {
            this.point += pointUsageDto.point
        }.let {
            userPointRepository.save(it)
        }.also {
            userPointHistoryService.save(pointUsageDto)
        }
    }

    @Transactional(readOnly = true)
    fun getOrCreateByUserId(userId: Long): UserPoint {
        return userPointRepository.findById(userId)
            .orElseGet {
                userPointRepository.save(UserPoint(userId = userId))
            }
    }



}