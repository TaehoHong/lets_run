package com.example.running.domain.app.controller.dto

data class VersionCheckResponse(
    val forceUpdate: Boolean,
    val minimumVersion: String? = null,
    val message: String? = null
)
