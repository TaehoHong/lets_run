package com.example.running.domain.user.service.dto

import com.example.running.domain.common.enums.AuthorityType

data class UserDto(
    val id: Long,
    val nickname: String,
    val email: String,
    val authorityType: AuthorityType
)