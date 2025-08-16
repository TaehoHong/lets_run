package com.example.running.domain.running.controller

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.*
import com.example.running.domain.running.service.RunningEndService
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.domain.running.service.RunningStartService
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.helper.authenticateWithUser
import com.example.running.utils.JwtPayloadParser
import com.example.running.utils.convertToOffsetDateTime
import org.springframework.web.bind.annotation.*


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordController(
    private val runningStartService: RunningStartService,
    private val runningEndService: RunningEndService,
    private val runningRecordService: RunningRecordService
){

    @PostMapping
    fun startRecord(@RequestBody creationRunningRecord: CreationRunningRecord): StartRunningResponse {
        return authenticateWithUser { userId ->
            runningStartService.startRecord(userId, creationRunningRecord.getStartDatetime())
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

    @PostMapping("/{id}/end")
    fun end(@PathVariable id: Long, @RequestBody request: EndRunningRequest):  EndRunningResponse {
        return runningEndService.end(
            RunningRecordUpdateDto(
                userId = JwtPayloadParser.getUserId(),
                runningRecordId = id,
                distance = request.distance,
                durationSec = request.durationSec,
                cadence = request.cadence,
                heartRate = request.heartRate,
                calorie = request.calorie
            )
        ).let {
            EndRunningResponse(it)
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long,
                  @RequestParam(required = false, defaultValue = "UTC") timezone: String,
                  @RequestBody updateRequest: UpdateRecordRequest) {

        runningRecordService.updateRecord(
            RunningRecordUpdateDto(
                userId = JwtPayloadParser.getUserId(),
                runningRecordId = id,
                shoeId = updateRequest.shoeId,
                distance = updateRequest.distance,
                durationSec = updateRequest.durationSec,
                cadence = updateRequest.cadence,
                heartRate = updateRequest.heartRate,
                calorie = updateRequest.calorie,
                startDatetime = updateRequest.startTimestamp?.let{ convertToOffsetDateTime(it, timezone) } ,
                endDatetime = updateRequest.endTimestamp?.let { convertToOffsetDateTime(it, timezone) }
            )
        )
    }
}