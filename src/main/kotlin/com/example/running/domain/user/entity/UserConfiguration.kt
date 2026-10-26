package com.example.running.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

    @Column(name = "language", nullable = false, length = 10)
    var language: String = "system",

    @Column(name = "language_id", columnDefinition = "SMALLINT UNSIGNED")
    var languageId: Int? = null,

    @Column(name = "health_import_first_enabled_at", columnDefinition = "DATETIME(3)")
    var healthImportFirstEnabledAt: OffsetDateTime? = null,

    @Column(name = "health_import_reward_cutoff_at", columnDefinition = "DATETIME(3)")
    var healthImportRewardCutoffAt: OffsetDateTime? = null,
) {
    fun update(healthImportEnabled: Boolean?, language: String? = null,
               updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)) {
        healthImportEnabled?.let { enabled ->
            if (enabled || this.healthImportEnabled) {
                if (!this.healthImportEnabled && healthImportFirstEnabledAt == null &&
                    healthImportRewardCutoffAt == null && healthImportLastSyncedAt == null) {
                    healthImportFirstEnabledAt = updatedAt
                }
                ensureHealthImportRewardCutoff(updatedAt)
            }
            this.healthImportEnabled = enabled
        }
        language?.let { this.language = it }
    }

    fun ensureHealthImportRewardCutoff(now: OffsetDateTime): OffsetDateTime {
        return healthImportRewardCutoffAt ?: (healthImportFirstEnabledAt ?: healthImportLastSyncedAt ?: now)
            .also { healthImportRewardCutoffAt = it }
    }

    fun markHealthImportLastSynced(lastSyncedAt: OffsetDateTime) {
        this.healthImportLastSyncedAt = lastSyncedAt
    }
}
