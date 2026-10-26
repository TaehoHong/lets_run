package com.example.running.domain.garmin.controller.dto

import java.time.Instant

class GarminAuthorizationResponse(
    val authorizationUrl: String,
    val state: String,
    val expiresAt: Instant,
)
