package com.example.running.domain.user_auth.service

import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.domain.user_auth.controller.dto.TokenResponse
import com.example.running.domain.user_auth.service.dto.OAuthAccountInfo
import com.example.running.domain.user_auth.service.dto.UserCreationDto
import com.example.running.exception.ApiError
import com.example.running.security.service.TokenService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserSignUpService(
    private val userService: UserService,
    private val userAccountService: UserAccountService,
    private val tokenService: TokenService
) {

    @Transactional(rollbackFor = [Exception::class])
    fun signup(oAuthAccountInfo: OAuthAccountInfo): TokenResponse {
        return (userAccountService.getByEmail(oAuthAccountInfo.email)
            ?: createUserAndGetUserAccount(oAuthAccountInfo))
            .run {
                tokenService.generateTokens(
                    userId = this.user.id,
                    nickname = this.user.nickname,
                    email = this.email,
                    authorityType = this.user.authorityType
                )
            }
    }

    private fun createUserAndGetUserAccount(oAuthAccountInfo: OAuthAccountInfo): UserAccount {
        return oAuthAccountInfo.let {
            UserCreationDto(
                email = it.email,
                nickname = it.name
            )
        }.let {
            userService.save(it)
            userAccountService.getByEmail(it.email)
                ?: run { throw RuntimeException(ApiError.NOT_FOUND_USER_ACCOUNT.message) }
        }
    }
}