package com.example.running.domain.point.controller.dto

import com.example.running.domain.point.entity.PointType
import java.time.OffsetDateTime


class UserPointHistoryRequest(
    val cursor: Long? = null,
    val isEarned: Boolean? = null,
    val startCreatedTimestamp: Long? = null,
    val size: Int = 30
)

class UserPointHistoryResponse(
    val id: Long,
    val point: Int,
    val pointType: String,
    val createdTimestamp: Long
) {
    constructor(id: Long, point: Int, pointType: PointType, createDateTime: OffsetDateTime): this(
        id = id,
        point = point,
        pointType = pointType.name,
        createdTimestamp = createDateTime.toEpochSecond()
    )
}