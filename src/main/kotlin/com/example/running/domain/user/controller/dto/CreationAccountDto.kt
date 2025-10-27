package com.example.running.domain.user.controller.dto

import com.example.running.domain.common.enums.AccountTypeName

class CreationAccountRequest(
    val provider: AccountTypeName,
    val code: String
)