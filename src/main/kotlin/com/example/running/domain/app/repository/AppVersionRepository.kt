package com.example.running.domain.app.repository

import com.example.running.domain.app.entity.AppVersion
import com.example.running.domain.app.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AppVersionRepository : JpaRepository<AppVersion, Long> {
    fun findByPlatformAndIsEnabledTrue(platform: Platform): Optional<AppVersion>
}
