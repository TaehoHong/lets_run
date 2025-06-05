package com.example.running.domain.user_auth.controller.dto

import com.example.running.domain.common.enums.AccountTypeName

open class OauthLoginRequest(
    val accountType: AccountTypeName
)