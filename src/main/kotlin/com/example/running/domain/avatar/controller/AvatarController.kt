package com.example.running.domain.avatar.controller

import com.example.running.domain.avatar.service.AvatarService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/avatars")
@RestController
class AvatarController(
    private val avatarService: AvatarService
) {

    @GetMapping("/main")
    fun getMain(): String {

        return "avatar"

    }
}