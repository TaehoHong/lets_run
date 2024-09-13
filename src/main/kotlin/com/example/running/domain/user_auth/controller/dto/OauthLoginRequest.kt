package com.example.running.user.controller.dto

import com.example.running.user.enums.AccountTypeName

open class OauthLoginRequest(
    val accountType: AccountTypeName
)