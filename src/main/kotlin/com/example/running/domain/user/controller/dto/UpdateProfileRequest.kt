package com.example.running.domain.user.controller.dto

import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(min = 1, max = 20, message = "닉네임은 1자 이상 20자 이하로 입력해주세요.")
    val nickname: String? = null
)
