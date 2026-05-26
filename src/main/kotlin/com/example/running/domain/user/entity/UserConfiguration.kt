package com.example.running.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "user_configuration")
class UserConfiguration(

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userId: Long = 0,

    @Column(name = "health_import_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    var healthImportEnabled: Boolean = false,

    @Column(name = "health_import_last_synced_at", columnDefinition = "DATETIME")
    var healthImportLastSyncedAt: OffsetDateTime? = null,
) {
    fun update(healthImportEnabled: Boolean?) {
        healthImportEnabled?.let { this.healthImportEnabled = it }
    }

    fun markHealthImportSynced(lastSyncedAt: OffsetDateTime) {
        this.healthImportEnabled = true
        this.healthImportLastSyncedAt = lastSyncedAt
    }
}
