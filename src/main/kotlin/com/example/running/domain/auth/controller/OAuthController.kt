package com.example.running.domain.auth.controller

import com.example.running.domain.auth.controller.dto.TokenResponse
import com.example.running.domain.auth.service.OAuthService
import com.example.running.domain.auth.service.UserSignUpService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.security.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.*

//http://localhost:8080/login/oauth2/code/google?code=4%2F0AQlEd8yywxIkfIjJ2lriidY4mX1sD7_flQX6MAo-R5OiNmQ5r9ikvZjy9T-GxOzefDKhnQ&scope=email+openid+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email&authuser=0&prompt=consent

@RequestMapping("/api/v1/oauth/{provider}")
@RestController
class OAuthController(
    private val objectMapper: ObjectMapper,
    private val oauthService: OAuthService,
    private val tokenService: TokenService,
    private val userSignUpService: UserSignUpService
) {

    private val log = KotlinLogging.logger {}

    @Operation(
        summary = "구글 로그인/회원가입",
        description = "구글 OAuth2 계정이 없는 경우 회원가입"
    )
    @GetMapping
    fun getToken(@PathVariable provider: String, @RequestParam code: String): TokenResponse {
        val accountTypeName = AccountTypeName.getByNameIgnoreCase(provider)

        return runCatching {
            oauthService.getOAuthAccountInfo(accountTypeName, code)
                .let {
                    userSignUpService.signup(accountTypeName, it)
                }.also { log.info { "access token : ${it.accessToken}" } }
        }.getOrElse {
            throw RuntimeException("Google Token parse Error")
        }
    }
}