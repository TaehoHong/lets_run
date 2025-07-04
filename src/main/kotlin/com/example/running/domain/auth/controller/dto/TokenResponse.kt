package com.example.running.domain.auth.controller.dto

class TokenResponse(
    val userId: Long,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String
)