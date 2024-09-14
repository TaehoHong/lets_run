package com.example.running.domain.avatar.controller


import com.example.running.domain.avatar.controller.dto.AvatarResponse
import com.example.running.domain.avatar.service.AvatarService
import com.example.running.utils.JwtPayloadParser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/avatars")
@RestController
class AvatarController(
    private val avatarService: AvatarService
) {

    @GetMapping("/main")
    fun getMain(): AvatarResponse {

        return AvatarResponse(
            avatarService.getMainAvatar(JwtPayloadParser.getUserId())
        )
    }
}