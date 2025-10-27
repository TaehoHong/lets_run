package com.example.running.domain.running.service

import com.example.running.domain.running.enums.RunningStatisticType
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningStatistics
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RunningStatisticService(
    private val runningRecordRepository: RunningRecordRepository
) {

    fun getStatistics(
        userId: Long,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        statisticType: RunningStatisticType
    ): RunningStatistics {

        return RunningStatistics(
            runningRecordRepository.getAllByUserIdAndEndDatetimeBetween(
                userId,
                OffsetDateTime.of(startDateTime, ZoneOffset.UTC),
                OffsetDateTime.of(endDateTime, ZoneOffset.UTC),
            ),
            statisticType
        )
    }

//    private fun getStartAndEndDateTime(date: LocalDate, statisticType: RunningStatisticType): Pair<OffsetDateTime, OffsetDateTime> {
//        return when (statisticType) {
//            RunningStatisticType.WEEKLY -> now.with(DayOfWeek.MONDAY)
//            RunningStatisticType.MONTHLY -> now.withDayOfMonth(1)
//            RunningStatisticType.YEARLY -> now.withDayOfYear(1)
//        }
//    }
}