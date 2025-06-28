package com.example.running.domain.shoe.service.dto

class ShoePatchDto (
    val brand: String? = null,
    val model: String? = null,
    val targetDistance: Int? = null,
    val isMain: Boolean? = null,
    val isEnabled: Boolean? = null,
    val isDeleted: Boolean? = null
)