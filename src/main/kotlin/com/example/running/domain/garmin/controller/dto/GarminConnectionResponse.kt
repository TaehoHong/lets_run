package com.example.running.domain.garmin.controller.dto

import com.example.running.domain.garmin.repository.GarminConnectionStatus
import java.time.Instant

class GarminConnectionResponse(
    val available: Boolean,
    val status: GarminConnectionStatus,
    val connectedAt: Instant? = null,
    val lastReceivedAt: Instant? = null,
    val lastImportedAt: Instant? = null,
    val lastImportedCount: Int = 0,
    val permissionGranted: Boolean = false,
)
