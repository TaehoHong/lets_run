package com.example.running.domain.running.service

import com.example.running.domain.running.enums.RunningStatisticType
import com.example.running.domain.running.repository.RunningRecordQueryRepository
import com.example.running.domain.running.service.dto.RunningStatistics
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class RunningStatisticService(
    private val runningRecordQueryRepository: RunningRecordQueryRepository
) {

    fun getStatistics(userId: Long, zoneId: ZoneId, statisticType: RunningStatisticType): RunningStatistics {

        val now = OffsetDateTime.now(zoneId)

        return RunningStatistics(
            runningRecordQueryRepository.getAllByUserIdAndEndDatetimeBetween(
                userId,
                getStartDateTime(now, statisticType),
                now
            ),
            statisticType
        )
    }

    private fun getStartDateTime(now: OffsetDateTime, statisticType: RunningStatisticType): OffsetDateTime {
        return when (statisticType) {
            RunningStatisticType.WEEKLY -> now.with(DayOfWeek.MONDAY)
            RunningStatisticType.MONTHLY -> now.withDayOfMonth(1)
            RunningStatisticType.YEARLY -> now.withDayOfYear(1)
        }
    }
}