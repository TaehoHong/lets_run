package com.example.running.domain.app.controller

import com.example.running.domain.app.controller.dto.VersionCheckResponse
import com.example.running.domain.app.entity.Platform
import com.example.running.domain.app.service.AppVersionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/app")
@RestController
class AppController(
    private val appVersionService: AppVersionService
) {

    @GetMapping("/version-check")
    fun checkVersion(
        @RequestParam platform: Platform,
        @RequestParam currentVersion: String
    ): VersionCheckResponse {
        return appVersionService.checkVersion(platform, currentVersion)
    }
}
