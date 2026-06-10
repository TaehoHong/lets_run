package com.example.running.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthyCheckController(
    @Value("\${server.name}")
    private val serverName: String,
    @Value("\${server.type}")
    private val activeType: String,
    private val dataSource: DataSource,
) {

    @GetMapping("/healthy-check")
    fun healthyCheck(): ResponseEntity<String> {
        return if (isDatabaseHealthy()) {
            ResponseEntity.ok(serverName)
        } else {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable")
        }
    }

    @GetMapping("/active-type")
    fun activeType(): String {
        return activeType
    }

    private fun isDatabaseHealthy(): Boolean {
        return try {
            val connection = dataSource.connection
            try {
                connection.isValid(2)
            } finally {
                connection.close()
            }
        } catch (e: Exception) {
            false
        }
    }
}
