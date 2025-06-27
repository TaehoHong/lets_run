package com.example.running.domain.shoe.controller.dto

import com.example.running.domain.shoe.service.dto.ShoeDto

class ShoeResponse (
    val id: Long,
    val brand: String,
    val model: String,
    val targetDistance: Int? = null,
    val totalDistance: Int,
    val isMain: Boolean,
    val isEnabled: Boolean
) {
    constructor(shoeDto: ShoeDto) : this(
        id = shoeDto.id,
        brand = shoeDto.brand,
        model = shoeDto.model,
        targetDistance = shoeDto.targetDistance,
        totalDistance = shoeDto.totalDistance,
        isMain = shoeDto.isMain,
        isEnabled = shoeDto.isEnabled
    )
}