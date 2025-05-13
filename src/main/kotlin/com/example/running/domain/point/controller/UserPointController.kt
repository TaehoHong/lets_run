package com.example.running.domain.point.controller

import com.example.running.domain.common.dto.PageResult
import com.example.running.domain.point.controller.dto.UserPointHistoryRequest
import com.example.running.domain.point.controller.dto.UserPointHistoryResponse
import com.example.running.domain.point.controller.dto.UserPointResponse
import com.example.running.domain.point.entity.UserPointHistory
import com.example.running.domain.point.service.UserPointHistoryService
import com.example.running.domain.point.service.UserPointService
import com.example.running.utils.JwtPayloadParser
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/users/points")
@RestController
class UserPointController(
    private val userPointService: UserPointService,
    private val userPointHistoryService: UserPointHistoryService
) {

    @GetMapping
    fun getPoint(): UserPointResponse {
        return userPointService.getOrCreateByUserId(JwtPayloadParser.getUserId())
            .let { UserPointResponse(it.userId, it.point) }
    }

    @GetMapping("/histories")
    fun getPointHistory(userPointHistoryRequest: UserPointHistoryRequest): List<UserPointHistoryResponse> {
        return userPointHistoryService.findAll(
            userId = JwtPayloadParser.getUserId(),
            id = userPointHistoryRequest.cursor,
            size = userPointHistoryRequest.size
        ).map {
            UserPointHistoryResponse(it.point, it.pointType, it.createdDatetime)
        }
    }
}