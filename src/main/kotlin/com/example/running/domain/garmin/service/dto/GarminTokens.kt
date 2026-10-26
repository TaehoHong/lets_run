package com.example.running.domain.garmin.service.dto

class GarminTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val refreshExpiresIn: Long?,
)
