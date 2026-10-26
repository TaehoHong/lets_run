package com.example.running.domain.running.controller

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.*
import com.example.running.domain.running.service.RunningEndService
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.domain.running.service.RunningStartService
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.helper.authenticateWithUser
import com.example.running.utils.JwtPayloadParser
import com.example.running.domain.running.service.RunningValidationService
import java.time.ZoneOffset
import org.springframework.web.bind.annotation.*
import java.time.Instant


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordController(
    private val runningStartService: RunningStartService,
    private val runningEndService: RunningEndService,
    private val runningRecordService: RunningRecordService,
    private val validation: RunningValidationService,
){

    @PostMapping
    fun startRecord(@RequestBody creationRunningRecord: CreationRunningRecord,
                    @RequestAttribute(name = "running.receivedAt", required = false) receivedAt: Instant? = null): StartRunningResponse {
        return authenticateWithUser { userId ->
            runningStartService.startRecord(userId, creationRunningRecord.startTimestamp?.let { validation.timestamp(it) }
                ?: (receivedAt ?: Instant.now()).atOffset(ZoneOffset.UTC))
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

    @GetMapping("/{id}/ownership")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    fun verifyOwnership(@PathVariable id: Long) {
        authenticateWithUser { userId -> runningRecordService.verifyOwnership(userId, id) }
    }

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): RunningRecordDetailResponse {
        return authenticateWithUser { userId ->
            runningRecordService.getDto(userId, id)
                .let { RunningRecordDetailResponse(it) }
        }
    }

    @PatchMapping("/{id}/shoe")
    fun updateShoe(
        @PathVariable id: Long,
        @RequestBody request: UpdateRunningRecordShoeRequest
    ): RunningRecordDetailResponse {
        return authenticateWithUser { userId ->
            runningRecordService.updateShoe(userId, id, request.shoeId)
                .let { RunningRecordDetailResponse(it) }
        }
    }

    @PostMapping("/{id}/end")
    fun end(@PathVariable id: Long, @RequestBody request: EndRunningRequest,
            @RequestAttribute(name = "running.receivedAt", required = false) receivedAt: Instant? = null): EndRunningResponse {
        return runningEndService.end(
            RunningRecordUpdateDto(
                userId = JwtPayloadParser.getUserId(),
                runningRecordId = id,
                distance = request.distance,
                durationSec = request.durationSec,
                cadence = request.cadence,
                heartRate = request.heartRate,
                calorie = request.calorie,
                endDatetime = request.endTimestamp?.let { validation.timestamp(it) }
            ), receivedAt ?: Instant.now()
        ).let {
            EndRunningResponse(it)
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long,
                  @RequestParam(required = false, defaultValue = "UTC") timezone: String,
                  @RequestBody updateRequest: UpdateRecordRequest,
                  @RequestAttribute(name = "running.receivedAt", required = false) receivedAt: Instant? = null) {

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
                startDatetime = updateRequest.startTimestamp?.let{ validation.timestamp(it, timezone) } ,
                endDatetime = updateRequest.endTimestamp?.let { validation.timestamp(it, timezone) }
            ), receivedAt ?: Instant.now()
        )
    }
}
