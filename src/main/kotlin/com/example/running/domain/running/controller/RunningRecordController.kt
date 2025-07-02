package com.example.running.domain.running.controller

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.CreationRunningRecord
import com.example.running.domain.running.controller.dto.EndRecordRequest
import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.controller.dto.RunningRecordSearchResponse
import com.example.running.domain.running.controller.dto.StartRunningResponse
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.helper.authenticateWithUser
import com.example.running.utils.JwtPayloadParser
import com.example.running.utils.convertToOffsetDateTime
import org.springframework.web.bind.annotation.*


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordController(
    private val runningRecordService: RunningRecordService
){

    @PostMapping
    fun startRecord(@RequestBody creationRunningRecord: CreationRunningRecord): StartRunningResponse {
        return authenticateWithUser { userId ->
            runningRecordService.startRecord(userId, creationRunningRecord.getStartDatetime())
                .let { StartRunningResponse(it.id) }
        }
    }

    @GetMapping
    fun search(@ModelAttribute request: RunningRecordSearchRequest): CursorResult<RunningRecordSearchResponse> {
        return authenticateWithUser { userId ->
            runningRecordService.getDtoCursorPage(userId, request)
                .let {
                    it.of { recordDto -> RunningRecordSearchResponse(recordDto) }
                }
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