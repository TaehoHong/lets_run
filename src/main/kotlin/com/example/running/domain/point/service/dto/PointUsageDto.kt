package com.example.running.domain.point.service.dto

class PointUsageDto(
    val userId: Long,
    val pointTypeId: Short,
    val point: Int,
    val runningRecordId: Long? = null,
    val itemId: Long? = null
)