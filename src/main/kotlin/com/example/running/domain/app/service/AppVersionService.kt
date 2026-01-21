package com.example.running.domain.app.service

import com.example.running.domain.app.controller.dto.VersionCheckResponse
import com.example.running.domain.app.entity.Platform
import com.example.running.domain.app.repository.AppVersionRepository
import org.springframework.stereotype.Service

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository
) {

    fun checkVersion(platform: Platform, currentVersion: String): VersionCheckResponse {
        val appVersion = appVersionRepository.findByPlatformAndIsEnabledTrue(platform)
            .orElse(null)

        if (appVersion == null) {
            return VersionCheckResponse(forceUpdate = false)
        }

        val minimumVersion = appVersion.minimumVersion
        val needsUpdate = compareVersions(currentVersion, minimumVersion) < 0

        return VersionCheckResponse(
            forceUpdate = needsUpdate,
            minimumVersion = if (needsUpdate) minimumVersion else null,
            message = if (needsUpdate) appVersion.message else null
        )
    }

    /**
     * Semantic Versioning 비교
     * @return 음수: v1 < v2, 0: v1 == v2, 양수: v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }

            if (part1 != part2) {
                return part1 - part2
            }
        }

        return 0
    }
}
