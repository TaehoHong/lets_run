package com.example.running.user.service.dto

import com.example.running.user.controller.dto.UserCreationRequest

class UserCreationDto(
    val email: String,
    val password: String,
    val nickname: String,
    val phoneNumber: String?
) {
    constructor(userCreationRequest: UserCreationRequest): this(
        email = userCreationRequest.email,
        password = userCreationRequest.password,
        nickname = userCreationRequest.nickname,
        phoneNumber = userCreationRequest.phoneNumber
    )
}