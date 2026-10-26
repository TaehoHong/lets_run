package com.example.running.domain.garmin.repository

import com.example.running.domain.activity.repository.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

typealias GarminConnectionStatus = ActivityConnectionStatus
typealias GarminConnection = ActivityConnection
typealias GarminOAuthState = ActivityOAuthState
typealias GarminInbox = ActivityInbox

@Repository
class GarminRepository(jdbc: JdbcTemplate) : ActivityProviderRepository(jdbc, "GARMIN") {
    fun connectionByGarminId(id: String): GarminConnection? = connectionByExternalId(id)
}
