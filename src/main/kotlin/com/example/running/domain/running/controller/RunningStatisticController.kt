package com.example.running.domain.running.controller

import com.example.running.domain.running.enums.RunningStatisticType
import com.example.running.domain.running.service.RunningStatisticService
import com.example.running.domain.running.service.dto.RunningStatistics
import com.example.running.utils.JwtPayloadParser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

@RequestMapping("/api/v1/running/statistics")
@RestController
class RunningStatisticController(
    private val runningStatisticService: RunningStatisticService
) {

    @GetMapping
    fun getStatistics(@RequestParam(required = false, defaultValue = "UTC") timezone: String,
                      @RequestParam statisticType: RunningStatisticType): RunningStatistics {

        return runningStatisticService.getStatistics(
            JwtPayloadParser.getUserId(),
            ZoneId.of(timezone),
            statisticType
        )
    }
}