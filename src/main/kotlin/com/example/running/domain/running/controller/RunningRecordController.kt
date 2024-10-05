package com.example.running.domain.running.controller

import com.example.running.domain.common.dto.PageResult
import com.example.running.domain.running.controller.dto.EndRecordRequest
import com.example.running.domain.running.controller.dto.RunningRecordSearch
import com.example.running.domain.running.controller.dto.StartRunningResponse
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.utils.JwtPayloadParser
import com.example.running.utils.convertToOffsetDateTime
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordController(
    private val runningRecordService: RunningRecordService
){


    @PostMapping
    fun startRecord(): StartRunningResponse {
        return runningRecordService.startRecord(JwtPayloadParser.getUserId())
            .let { StartRunningResponse(it.id) }
    }

    @GetMapping
    fun search(pageable: Pageable): PageResult<RunningRecordSearch.Response> {
        return runningRecordService.getDtoPage(JwtPayloadParser.getUserId(), pageable)
            .let {
                PageResult.of(it) { record -> RunningRecordSearch.Response(record) }
            }
    }

    @PutMapping("/{id}/end")
    fun endRecord(@PathVariable id: Long,
                  @RequestParam(required = false, defaultValue = "UTC") timezone: String,
                  @RequestBody endRecord: EndRecordRequest) {

        runningRecordService.updateRecord(
            RunningRecordUpdateDto(
                userId = JwtPayloadParser.getUserId(),
                runningRecordId = id,
                distance = endRecord.distance,
                durationSec = endRecord.durationSec,
                cadence = endRecord.cadence,
                heartRate = endRecord.heartRate,
                calorie = endRecord.calorie,
                endDateTime = convertToOffsetDateTime(endRecord.endTimestamp, timezone)
            )
        )
    }
}