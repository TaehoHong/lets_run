package com.example.running.user.controller

import com.example.running.user.controller.dto.VerificationEmailDto
import com.example.running.user.service.UserAccountService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    val userAccountService: UserAccountService
) {

    @PostMapping("/verification/email")
    fun verifyEmailIsNotDuplicate(@Valid @RequestBody emailDto: VerificationEmailDto) {

        userAccountService.verifyEmailIsNotExists(emailDto.email)
    }
}