package com.example.running.user.controller.dto

import com.example.running.user.enums.AccountTypeName

class LoginGoogleRequest(
    val authorizationCode: String
): OauthLoginRequest(AccountTypeName.GOOGLE)