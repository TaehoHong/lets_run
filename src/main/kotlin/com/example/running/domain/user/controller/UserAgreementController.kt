package com.example.running.domain.user.controller

import com.example.running.domain.user.controller.dto.PatchUserAgreementRequests
import com.example.running.domain.user.service.UserAgreementService
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/users/agreement")
@RestController
class UserAgreementController(
    val userAgreementService: UserAgreementService
) {

    @PatchMapping
    fun patchAll(@RequestBody requests: PatchUserAgreementRequests) {
        authenticateWithUser { userId ->
            userAgreementService.patch(userId, requests.requests)
        }
    }
}