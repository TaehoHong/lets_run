package com.example.running.domain.user_auth.controller

import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user_auth.controller.dto.TokenResponse
import com.example.running.domain.user_auth.service.GoogleOauthService
import com.example.running.domain.user_auth.service.UserSignUpService
import com.example.running.domain.user_auth.service.dto.OAuthAccountInfo
import com.example.running.security.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

//http://localhost:8080/login/oauth2/code/google?code=4%2F0AQlEd8yywxIkfIjJ2lriidY4mX1sD7_flQX6MAo-R5OiNmQ5r9ikvZjy9T-GxOzefDKhnQ&scope=email+openid+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email&authuser=0&prompt=consent

@RequestMapping("/api/v1/oauth/google")
@RestController
class GoogleOauthController(
    private val objectMapper: ObjectMapper,
    private val googleOauthService: GoogleOauthService,
    private val tokenService: TokenService,
    private val userSignUpService: UserSignUpService
) {

    private val log = KotlinLogging.logger{}

    @Operation(
        summary = "구글 로그인/회원가입",
        description = "구글 OAuth2 계정이 없는 경우 회원가입"
    )
    @GetMapping
    fun getToken(@RequestParam code: String): TokenResponse {
        return googleOauthService.requestToken(code)
            .let {
                log.info { "idToken : ${it.idToken}" }
                log.info { "google token : ${it.accessToken}" }
                tokenService.decodeTokenPayload(it.idToken)
                    .let {
                        log.info { "it: $it" }
                        objectMapper.readValue(it, OAuthAccountInfo::class.java)
                    }
            }?.let {
                userSignUpService.signup(AccountTypeName.GOOGLE, it)
            }?.also { log.info { "access token : ${it.accessToken}"} }
            ?: run { throw RuntimeException("Google Token parse Error")}
    }
}