package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.service.dto.StartRunningDto
import com.example.running.domain.shoe.service.ShoeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RunningStartService(
    private val shoeService: ShoeService,
    private val runningRecordService: RunningRecordService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun startRecord(userId: Long, startDateTime: OffsetDateTime): StartRunningDto {
        runningRecordService.endPreviousRecords(userId)
        val shoeId = shoeService.findMainShoe(userId)?.id

        return runningRecordService.save(
            RunningRecord(userId = userId, shoeId = shoeId, startDateTime = startDateTime)
        ).let {
            StartRunningDto(it.id)
        }
    }
}