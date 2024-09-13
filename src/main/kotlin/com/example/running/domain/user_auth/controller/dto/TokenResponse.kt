package com.example.running.user.controller.dto

class TokenResponse(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String
)