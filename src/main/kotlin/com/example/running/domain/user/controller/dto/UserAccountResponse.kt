package com.example.running.domain.user.controller.dto

import com.example.running.domain.common.enums.AccountTypeName

class UserAccountResponse(
    val id: Long,
    val email: String,
    val accountType: AccountTypeName
)