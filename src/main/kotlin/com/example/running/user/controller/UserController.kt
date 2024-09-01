package com.example.running.user.controller

import com.example.running.user.controller.dto.UserCreationRequest
import com.example.running.user.controller.dto.UserResponse
import com.example.running.user.controller.dto.VerificationEmailDto
import com.example.running.user.service.UserAccountService
import com.example.running.user.service.UserService
import com.example.running.user.service.dto.UserCreationDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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