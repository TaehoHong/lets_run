package com.example.running.domain.point.controller

import com.example.running.domain.point.controller.dto.UserPointResponse
import com.example.running.domain.point.service.UserPointService
import com.example.running.utils.JwtPayloadParser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/users/points")
@RestController
class UserPointController(
    private val userPointService: UserPointService
) {

    @GetMapping
    fun getPoint(): UserPointResponse {
        return userPointService.getOrCreateByUserId(JwtPayloadParser.getUserId())
            .let { UserPointResponse(it.userId, it.point) }
    }
}