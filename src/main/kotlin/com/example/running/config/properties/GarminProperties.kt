package com.example.running.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "garmin")
class GarminProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val encryptionKey: String = "",
    val webhookToken: String = "",
    val redirectUri: String = "https://dev-api-run-taeho.com:12643/api/v1/garmin/callback",
) {
    init {
        require(!enabled || (clientId.isNotBlank() && clientSecret.isNotBlank())) {
            "Garmin client credentials are required when enabled"
        }
    }
}
