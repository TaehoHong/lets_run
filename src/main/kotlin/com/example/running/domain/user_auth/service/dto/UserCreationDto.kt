package com.example.running.domain.user_auth.service.dto

import com.example.running.domain.common.enums.AccountTypeName

class UserCreationDto(
    val email: String,
    val password: String? = null,
    val accountType: AccountTypeName,
    val nickname: String
) {
//    constructor(userCreationRequest: UserCreationRequest): this(
//        email = userCreationRequest.email,
//        password = userCreationRequest.password,
//        nickname = userCreationRequest.nickname
//    )
}