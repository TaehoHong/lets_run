package com.example.running.domain.running.controller

import com.example.running.domain.running.enums.RunningStatisticType
import com.example.running.domain.running.service.RunningStatisticService
import com.example.running.domain.running.service.dto.RunningStatistics
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DateTimeException
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException

@RequestMapping("/api/v1/running/statistics")
@RestController
class RunningStatisticController(
    private val runningStatisticService: RunningStatisticService
) {

    @GetMapping
    fun getStatistics(
        @RequestParam startDateTime: LocalDateTime,
        @RequestParam endDateTime: LocalDateTime,
        @RequestParam statisticType: RunningStatisticType,
        @RequestParam(defaultValue = "UTC") timeZone: String
    ): RunningStatistics {

        val zone = try {
            ZoneId.of(timeZone)
        } catch (_: DateTimeException) {
            throw ApiException(ApiError.BAD_REQUEST_FIELD_VALID_ERROR)
        }
        return authenticateWithUser { userId ->
            runningStatisticService.getStatistics(userId, startDateTime, endDateTime, statisticType, zone)
        }
    }
}