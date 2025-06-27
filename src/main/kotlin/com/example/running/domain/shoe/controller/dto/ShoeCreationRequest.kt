package com.example.running.domain.shoe.controller.dto

class ShoeCreationRequest (
    val brand: String,
    val model: String,
    val targetDistance: Int? = null,
    val isMain: Boolean = false
)