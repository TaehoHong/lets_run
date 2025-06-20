package com.example.running.domain.user.controller

import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.domain.user_auth.controller.dto.VerificationEmailDto
import com.example.running.helper.authenticateWithUser
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

    //자체계정 미제공으로 주석처리
//    @PostMapping
//    fun createUser(@Valid @RequestBody userCreationRequest: UserCreationRequest): UserResponse {
//
//        userAccountService.verifyEmailIsNotExists(userCreationRequest.email)
//        return userService.save(UserCreationDto(userCreationRequest))
//            .let { UserResponse(it.id, it.nickname) }
//    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): String {

        return "유저유저"
    }

    @GetMapping("/me")
    fun getMe(): UserDataDto {
        return authenticateWithUser { userId ->
            userService.getUserDataDto(userId)
        }
    }
}