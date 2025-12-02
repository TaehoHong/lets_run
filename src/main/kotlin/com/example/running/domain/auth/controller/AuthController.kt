package com.example.running.domain.auth.controller

import com.example.running.config.properties.JwtProperties
import com.example.running.domain.auth.controller.dto.TokenResponse
import com.example.running.domain.user.service.UserAgreementService
import com.example.running.domain.user.service.UserService
import com.example.running.security.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    val tokenService: TokenService,
    val userService: UserService,
    val userAgreementService: UserAgreementService
) {

    @PostMapping("/refresh")
    fun refreshToken(@RequestHeader(JwtProperties. REFRESH_TOKEN_HEADER) bearerToken: String): TokenResponse {
        return bearerToken.replace("Bearer", "").trim()
            .let { refreshToken ->
                val userId = tokenService.getId(refreshToken)
                userService.getUserDto(userId.toLong())
            }.let {
                val isAgreedOnTerms = userAgreementService.isAllTermsAgreed(it.id)
                tokenService.generateTokens(it.id, it.nickname, isAgreedOnTerms, it.authorityType)
            }
    }
}