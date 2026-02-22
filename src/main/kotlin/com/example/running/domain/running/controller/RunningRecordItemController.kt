package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.GetRunningRecordItemsResponse
import com.example.running.domain.running.controller.dto.PostRequest
import com.example.running.domain.running.controller.dto.RunningRecordItemResponseDto
import com.example.running.domain.running.service.RunningRecordItemService
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto as ServiceRunningRecordItemGpsPointDto
import com.example.running.utils.JwtPayloadParser
import com.example.running.utils.convertToOffsetDateTime
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
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
        val userId = JwtPayloadParser.getUserId()

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
                gpsPoints = item.gpsPoints?.map {
                    ServiceRunningRecordItemGpsPointDto(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        timestampMs = it.timestampMs,
                        speed = it.speed,
                        altitude = it.altitude,
                        accuracy = it.accuracy,
                    )
                } ?: emptyList(),
            )
        }.let {
            runningRecordItemService.save(userId, runningRecordId, it)
        }
    }

    @GetMapping("/{id}/items")
    fun getRecordItems(@PathVariable(name = "id") runningRecordId: Long): GetRunningRecordItemsResponse {
        val userId = JwtPayloadParser.getUserId()
        val items = runningRecordItemService.getItems(userId, runningRecordId)
            .map(::RunningRecordItemResponseDto)

        return GetRunningRecordItemsResponse(items)
    }
}
