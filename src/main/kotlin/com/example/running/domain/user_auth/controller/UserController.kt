package com.example.running.domain.user_auth.controller

import com.example.running.domain.common.service.UserAccountService
import com.example.running.domain.common.service.UserService
import com.example.running.domain.user_auth.controller.dto.UserCreationRequest
import com.example.running.domain.user_auth.controller.dto.UserResponse
import com.example.running.domain.user_auth.controller.dto.VerificationEmailDto
import com.example.running.domain.user_auth.service.dto.UserCreationDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    val userAccountService: UserAccountService,
    val userService: UserService
) {

    @PostMapping("/verification/email")
    fun verifyEmailIsNotDuplicate(@Valid @RequestBody emailDto: VerificationEmailDto) {

        userAccountService.verifyEmailIsNotExists(emailDto.email)
    }

    @PostMapping
    fun createUser(@Valid @RequestBody userCreationRequest: UserCreationRequest): UserResponse {

        userAccountService.verifyEmailIsNotExists(userCreationRequest.email)
        return userService.save(UserCreationDto(userCreationRequest))
            .let { UserResponse(it.id, it.nickname) }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): String {

        return "유저유저"
    }
}