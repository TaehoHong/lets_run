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
        // 동일 이메일이 다른 provider로 등록될 수 있으므로 email + accountType + isDeleted=false로 조회
        val existingAccount = userAccountService.getByEmailAndAccountType(oAuthAccountInfo.email, accountType)

        // 활성 계정이 있으면 반환, 없으면 새로 생성
        return existingAccount ?: createUserAndGetUserAccount(accountType, oAuthAccountInfo)
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
            userAccountService.getByEmailAndAccountType(it.email, accountType)
                ?: run { throw RuntimeException(ApiError.NOT_FOUND_USER_ACCOUNT.message) }
        }
    }

    private fun generateNickname(): String {
        return "태호군#" + UUID.randomUUID().toString().substring(0, 5)
    }
}