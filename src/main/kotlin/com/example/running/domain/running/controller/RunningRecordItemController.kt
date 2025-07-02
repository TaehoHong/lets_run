package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.PostRequest
import com.example.running.domain.running.service.RunningRecordItemService
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.utils.convertToOffsetDateTime
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordItemController(
    private val runningRecordItemService: RunningRecordItemService
){
    
    @PostMapping("/{id}/items")
    fun recordItem(@PathVariable(name = "id") runningRecordId: Long,
                   @Valid @RequestBody postRequest: PostRequest
    ) {
        postRequest.items.map { item ->
            RunningRecordItemDto(
                distance = item.distance,
                durationSec = item.durationSec,
                cadence = item.cadence,
                heartRate = item.heartRate,
                minHeartRate = item.minHeartRate,
                maxHeartRate = item.maxHeartRate,
                orderIndex = item.orderIndex,
                startDateTime = convertToOffsetDateTime(item.startTimeStamp),
                endDateTime = convertToOffsetDateTime(item.endTimeStamp),
            )
        }.let {
            runningRecordItemService.save(runningRecordId, it)
        }
    }
}