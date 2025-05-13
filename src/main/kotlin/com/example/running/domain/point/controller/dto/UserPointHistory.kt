package com.example.running.domain.point.controller.dto

import com.example.running.domain.point.entity.PointType
import java.time.OffsetDateTime


class UserPointHistoryRequest(
    val cursor: Long? = null,
    val size: Int = 30
)

class UserPointHistoryResponse(
    val point: Int,
    val pointType: String,
    val timestamp: Long
) {
    constructor(point: Int, pointType: PointType, createDateTime: OffsetDateTime): this(
        point = point,
        pointType = pointType.name,
        timestamp = createDateTime.toEpochSecond()
    )
}