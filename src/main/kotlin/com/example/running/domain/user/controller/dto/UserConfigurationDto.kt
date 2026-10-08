package com.example.running.domain.user.controller.dto

import com.example.running.domain.user.entity.UserConfiguration
import jakarta.validation.constraints.Pattern

class UserConfigurationResponse(
    val userId: Long,
    val healthImportEnabled: Boolean,
    val healthImportLastSyncedTimestamp: Long?,
    val language: String,
) {
    constructor(configuration: UserConfiguration) : this(
        userId = configuration.userId,
        healthImportEnabled = configuration.healthImportEnabled,
        healthImportLastSyncedTimestamp = configuration.healthImportLastSyncedAt?.toEpochSecond(),
        language = configuration.language,
    )
}

class UpdateUserConfigurationRequest(
    val healthImportEnabled: Boolean? = null,
    @field:Pattern(regexp = "system|ko|ja", message = "Unsupported language")
    val language: String? = null,
)
