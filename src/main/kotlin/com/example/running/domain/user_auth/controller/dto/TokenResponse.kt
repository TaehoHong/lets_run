package com.example.running.domain.user_auth.controller.dto

class TokenResponse(
    val userId: Long,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String
)