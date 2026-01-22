package com.example.running.domain.user.controller

import com.example.running.domain.auth.service.OAuthService
import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.controller.dto.CreationAccountRequest
import com.example.running.domain.user.controller.dto.UserAccountResponse
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/users/accounts")
@RestController
class UserAccountController(
    private val userService: UserService,
    private val userAccountService: UserAccountService,
    private val oAuthService: OAuthService,
    private val oAuthTokenService: OAuthTokenService,
) {

    @DeleteMapping("/{id}")
    fun deleteAccount(@PathVariable id: Long) {
        authenticateWithUser { userId ->
            userAccountService.hardDelete(id, userId)
        }
    }

    @PostMapping
    fun createAccount(@RequestBody request: CreationAccountRequest): UserAccountResponse {
        return authenticateWithUser { userId ->
            val loginResult = oAuthService.getOAuthLoginResult(request.provider, request.code)

            val userAccount = userAccountService.save(
                user = userService.getById(userId),
                email = loginResult.accountInfo.email,
                accountType = request.provider
            )

            // Apple 계정인 경우 refresh token 저장
            if (request.provider == AccountTypeName.APPLE && loginResult.refreshToken != null) {
                oAuthTokenService.saveOrUpdate(userAccount, loginResult.refreshToken)
            }

            UserAccountResponse(
                id = userAccount.id,
                email = userAccount.email,
                accountType = request.provider
            )
        }
    }
}