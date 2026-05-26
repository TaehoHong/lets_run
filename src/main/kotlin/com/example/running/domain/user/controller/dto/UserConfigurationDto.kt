package com.example.running.domain.user.controller.dto

import com.example.running.domain.user.entity.UserConfiguration

class UserConfigurationResponse(
    val userId: Long,
    val healthImportEnabled: Boolean,
    val healthImportLastSyncedTimestamp: Long?,
) {
    constructor(configuration: UserConfiguration) : this(
        userId = configuration.userId,
        healthImportEnabled = configuration.healthImportEnabled,
        healthImportLastSyncedTimestamp = configuration.healthImportLastSyncedAt?.toEpochSecond(),
    )
}

class UpdateUserConfigurationRequest(
    val healthImportEnabled: Boolean? = null,
)
