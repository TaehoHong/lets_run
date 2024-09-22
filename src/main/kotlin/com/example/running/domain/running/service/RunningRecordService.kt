package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordQueryRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordService(
    private val runningRecordRepository: RunningRecordRepository,
    private val runningRecordQueryRepository: RunningRecordQueryRepository
) {

    @Transactional(readOnly = true)
    fun startRecord(userId: Long) {

        endRecord(userId)

        runningRecordRepository.save(
            RunningRecord(userId = userId)
        )
    }

    private fun endRecord(userId: Long) {
        runningRecordQueryRepository.updateIsEndById(true, userId)
    }
}