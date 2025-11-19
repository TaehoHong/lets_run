package com.example.running.domain.user.controller

import com.example.running.domain.auth.service.OAuthService
import com.example.running.domain.user.controller.dto.CreationAccountRequest
import com.example.running.domain.user.controller.dto.UserAccountResponse
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.helper.authenticateWithUser
import com.example.running.security.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/users/accounts")
@RestController
class UserAccountController(
    private val userService: UserService,
    private val userAccountService: UserAccountService,
    private val oAuthService: OAuthService,
) {

    @DeleteMapping("/{id}")
    fun deleteAccount(@PathVariable id: Long) {
        authenticateWithUser { userId ->
            userAccountService.softDelete(userId, id)
        }
    }

    @PostMapping
    fun createAccount(@RequestBody request: CreationAccountRequest): UserAccountResponse {
        return authenticateWithUser { userId ->
            oAuthService.getOAuthAccountInfo(request.provider, request.code)
                .let { oAuthAccountInfo ->
                    userAccountService.save(
                        user = userService.getById(userId),
                        email = oAuthAccountInfo.email,
                        accountType = request.provider
                    )
                }.let { userAccount ->
                    UserAccountResponse(
                        id = userAccount.id,
                        email = userAccount.email,
                        accountType = request.provider
                    )
                }
        }
    }
}