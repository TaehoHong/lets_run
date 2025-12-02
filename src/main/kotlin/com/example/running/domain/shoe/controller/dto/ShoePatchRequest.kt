package com.example.running.domain.shoe.controller.dto

class ShoePatchRequest (
    val brand: String? = null,
    val model: String? = null,
    val targetDistance: Int? = null,
    val isEnabled: Boolean? = null,
    val isDeleted: Boolean? = null
)