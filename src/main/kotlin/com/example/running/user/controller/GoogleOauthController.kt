package com.example.running.user.controller

import com.example.running.user.controller.dto.LoginGoogleRequest
import com.example.running.user.controller.dto.TokenResponse
import com.example.running.user.service.GoogleOauthService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/oauth/google")
@RestController
class GoogleOauthController(
    val googleOauthService: GoogleOauthService
) {

    @PostMapping
    fun getToken(@RequestBody loginGoogleRequest: LoginGoogleRequest): TokenResponse {

//        googleOauthService
        return TokenResponse("", "")
    }
}