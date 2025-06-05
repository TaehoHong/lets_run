package com.example.running.domain.user_auth.controller.dto

class TokenResponse(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String
)