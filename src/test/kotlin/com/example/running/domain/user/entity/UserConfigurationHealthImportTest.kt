package com.example.running.domain.user.entity

import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserConfigurationHealthImportTest {
    private val enabledAt = OffsetDateTime.parse("2026-10-01T00:00:00Z")

    @Test
    fun `first enable fixes both timestamps across repeated enables and re-enables`() {
        val configuration = UserConfiguration(7)

        configuration.update(true, updatedAt = enabledAt)
        configuration.markHealthImportLastSynced(enabledAt.plusDays(1))
        configuration.update(true, updatedAt = enabledAt.plusDays(2))
        configuration.update(false, updatedAt = enabledAt.plusDays(3))
        configuration.update(true, updatedAt = enabledAt.plusDays(4))

        assertEquals(enabledAt, configuration.healthImportFirstEnabledAt)
        assertEquals(enabledAt, configuration.healthImportRewardCutoffAt)
        assertEquals(enabledAt.plusDays(1), configuration.healthImportLastSyncedAt)
    }

    @Test
    fun `legacy cursor preserves reward cutoff without inventing a first activation`() {
        val configuration = UserConfiguration(7, healthImportLastSyncedAt = enabledAt.minusDays(1))

        configuration.update(true, updatedAt = enabledAt)

        assertNull(configuration.healthImportFirstEnabledAt)
        assertEquals(enabledAt.minusDays(1), configuration.healthImportRewardCutoffAt)
    }

    @Test
    fun `migrated cutoff remains fixed when a legacy user re-enables`() {
        val configuration = UserConfiguration(7, healthImportRewardCutoffAt = enabledAt)

        configuration.update(true, updatedAt = enabledAt.plusDays(1))

        assertNull(configuration.healthImportFirstEnabledAt)
        assertEquals(enabledAt, configuration.healthImportRewardCutoffAt)
    }

    @Test
    fun `already enabled legacy configuration uses server fallback without claiming first activation`() {
        val configuration = UserConfiguration(7, healthImportEnabled = true)

        assertEquals(enabledAt, configuration.ensureHealthImportRewardCutoff(enabledAt))
        assertEquals(enabledAt, configuration.ensureHealthImportRewardCutoff(enabledAt.plusDays(1)))
        assertNull(configuration.healthImportFirstEnabledAt)
    }

    @Test
    fun `disabling an uninitialized legacy configuration cannot turn re-enable into a known first activation`() {
        val configuration = UserConfiguration(7, healthImportEnabled = true)

        configuration.update(false, updatedAt = enabledAt)
        configuration.update(true, updatedAt = enabledAt.plusDays(1))

        assertEquals(enabledAt, configuration.healthImportRewardCutoffAt)
        assertNull(configuration.healthImportFirstEnabledAt)
    }

    @Test
    fun `language-only edits leave an unused Health configuration uninitialized`() {
        val configuration = UserConfiguration(7)

        configuration.update(null, language = "ja", updatedAt = enabledAt)

        assertNull(configuration.healthImportFirstEnabledAt)
        assertNull(configuration.healthImportRewardCutoffAt)
    }
}
