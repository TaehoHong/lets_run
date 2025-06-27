package com.example.running.domain.shoe.service.dto

class ShoeCreationDto(
    val userId: Long,
    val brand: String,
    val model: String,
    val targetDistance: Int? = null,
    val isMain: Boolean
)