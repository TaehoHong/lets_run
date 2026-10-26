package com.example.running.exception

class GarminApiException(
    val statusCode: Int,
    val credentialsInvalid: Boolean = false,
) : RuntimeException("Garmin request failed")
