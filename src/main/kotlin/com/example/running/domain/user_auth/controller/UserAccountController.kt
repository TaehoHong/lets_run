package com.example.running.domain.user_auth.controller

import com.example.running.domain.user_auth.controller.dto.LoginRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/accounts")
@RestController
class UserAccountController {

    @PostMapping("/login")
    fun login(@Valid @RequestBody loginRequest: LoginRequest) {

    }
}