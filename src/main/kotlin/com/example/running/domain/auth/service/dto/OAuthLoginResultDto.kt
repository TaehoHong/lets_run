package com.example.running.domain.auth.service.dto

/**
 * OAuth 로그인 결과 DTO
 * 계정 정보와 refresh token을 함께 반환
 */
data class OAuthLoginResultDto(
    val accountInfo: OAuthAccountInfoDto,
    val refreshToken: String?
)
