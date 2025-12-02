package com.example.running.domain.user.controller.dto

data class ProfileResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
)
