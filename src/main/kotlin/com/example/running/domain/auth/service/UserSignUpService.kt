package com.example.running.domain.auth.service

import com.example.running.domain.auth.service.dto.OAuthAccountInfoDto
import com.example.running.domain.auth.service.dto.UserCreationDto
import com.example.running.domain.avatar.service.AvatarService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserAgreementService
import com.example.running.domain.user.service.UserService
import com.example.running.exception.ApiError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*


@Service
class UserSignUpService(
    private val userService: UserService,
    private val userAccountService: UserAccountService,
    private val avatarService: AvatarService,
    private val userPointService: UserPointService,
    private val userAgreementService: UserAgreementService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun signup(accountType: AccountTypeName, oAuthAccountInfo: OAuthAccountInfoDto): UserAccount {
        val existingAccount = userAccountService.getByEmail(oAuthAccountInfo.email)
        
        return when {
            // 계정이 없거나 삭제된 계정인 경우 → 새로 생성
            existingAccount == null || existingAccount.isDeleted ->
                createUserAndGetUserAccount(accountType, oAuthAccountInfo)
            // 활성 계정인 경우 → 기존 계정 반환
            else -> existingAccount
        }
    }

    private fun createUserAndGetUserAccount(accountType: AccountTypeName, oAuthAccountInfo: OAuthAccountInfoDto): UserAccount {
        return oAuthAccountInfo.let {
            UserCreationDto(
                email = it.email,
                nickname = it.nickname?:generateNickname(),
                accountType = accountType,
            )
        }.also {
            val user = userService.save(it)
            avatarService.createDefault(user.id)
            userPointService.save(userId = user.id)
            userAgreementService.createDefault(user)
        }.let {
            userAccountService.getByEmail(it.email)
                ?: run { throw RuntimeException(ApiError.NOT_FOUND_USER_ACCOUNT.message) }
        }
    }

    private fun generateNickname(): String {
        return "태호군#" + UUID.randomUUID().toString().substring(0, 5)
    }
}