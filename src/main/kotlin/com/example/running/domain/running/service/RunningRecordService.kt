package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordQueryRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.StartRunningDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordService(
    private val runningRecordRepository: RunningRecordRepository,
    private val runningRecordQueryRepository: RunningRecordQueryRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun startRecord(userId: Long): StartRunningDto {

        endRecord(userId)

        return runningRecordRepository.save(
            RunningRecord(userId = userId)
        ).let {
            StartRunningDto(it.id)
        }
    }

    private fun endRecord(userId: Long) {
        runningRecordQueryRepository.updateIsEndById(true, userId)
    }
}