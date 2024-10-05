package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.PostRequest
import com.example.running.domain.running.service.RunningRecordItemService
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.utils.convertToOffsetDateTime
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordItemController(
    private val runningRecordItemService: RunningRecordItemService
){
    
    @PostMapping("/{id}/items")
    fun recordItem(@PathVariable(name = "id") runningRecordId: Long,
                   @Valid postRequest: PostRequest
    ) {
        RunningRecordItemDto(
            distance = postRequest.distance,
            durationSec = postRequest.durationSec,
            cadence = postRequest.cadence,
            heartRate = postRequest.heartRate,
            minHeartRate = postRequest.minHeartRate,
            maxHeartRate = postRequest.maxHeartRate,
            orderIndex = postRequest.orderIndex,
            startDateTime = convertToOffsetDateTime(postRequest.startTimeStamp),
            endDateTime = convertToOffsetDateTime(postRequest.endTimeStamp),
        ).let {
            runningRecordItemService.save(runningRecordId, it)
        }
    }
}