package com.example.running.domain.shoe.service.dto

import com.example.running.domain.shoe.entity.Shoe

class ShoeDto(
    val id: Long,
    val brand: String,
    val model: String,
    val targetDistance: Int? = null,
    val totalDistance: Int,
    val isMain: Boolean,
    val isEnabled: Boolean
) {
    constructor(shoe: Shoe): this(
        id = shoe.id,
        brand = shoe.brand,
        model = shoe.model,
        targetDistance = shoe.targetDistance,
        totalDistance = shoe.totalDistance,
        isMain = shoe.isMain,
        isEnabled = shoe.isEnabled
    )
}