package com.example.running.domain.user_auth.controller.dto

import com.example.running.domain.common.enums.AccountTypeName

class LoginGoogleRequest(
    val authorizationCode: String
): OauthLoginRequest(AccountTypeName.GOOGLE)