package com.example.running.utils

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId


fun convertToOffsetDateTime(timestamp: Long) = OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())