package com.example.running.domain.auth.service

import com.example.running.domain.auth.controller.dto.TokenResponse
import com.example.running.domain.auth.service.dto.OAuthAccountInfo
import com.example.running.domain.auth.service.dto.UserCreationDto
import com.example.running.domain.avatar.service.AvatarService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.exception.ApiError
import com.example.running.security.service.TokenService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*


@Service
class UserSignUpService(
    private val userService: UserService,
    private val userAccountService: UserAccountService,
    private val avatarService: AvatarService,
    private val tokenService: TokenService
) {

    @Transactional(rollbackFor = [Exception::class])
    fun signup(accountType: AccountTypeName, oAuthAccountInfo: OAuthAccountInfo): TokenResponse {
        return (userAccountService.getByEmail(oAuthAccountInfo.email)
            ?: createUserAndGetUserAccount(accountType, oAuthAccountInfo))
            .run {
                tokenService.generateTokens(
                    userId = this.user.id,
                    nickname = this.user.nickname,
                    email = this.email,
                    authorityType = this.user.authorityType
                )
            }
    }

    private fun createUserAndGetUserAccount(accountType: AccountTypeName, oAuthAccountInfo: OAuthAccountInfo): UserAccount {
        return oAuthAccountInfo.let {
            UserCreationDto(
                email = it.email,
                nickname = it.nickname?:generateNickname(),
                accountType = accountType,
            )
        }.let {
            val user = userService.save(it)
            avatarService.createDefault(user.id)
            userAccountService.getByEmail(it.email)
                ?: run { throw RuntimeException(ApiError.NOT_FOUND_USER_ACCOUNT.message) }
        }
    }

    private fun generateNickname(): String {
        return "태호군#" + UUID.randomUUID().toString().substring(0, 5)
    }
}