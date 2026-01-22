package com.example.running.domain.user.service

import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserAccountService(
    private val passwordEncoder: PasswordEncoder,
    private val userAccountRepository: UserAccountRepository,
    private val oAuthTokenService: OAuthTokenService
) {

    @Transactional(readOnly = true)
    fun verifyEmailIsNotExists(email: String) {
        // 활성화된 계정만 확인 (탈퇴한 계정은 제외)
        userAccountRepository.existsByEmailAndIsDeletedFalse(email)
            .alsoIfTrue {
                throw RuntimeException("이미 존재하는 이메일입니다.")
            }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun save(user: User, email: String, password: String? = null, accountType: AccountTypeName): UserAccount {
        return userAccountRepository.save(
            UserAccount(
                user = user,
                accountTypeId = accountType.id,
                email = email,
                password = password?.let { passwordEncoder.encode(it) }
            )
        )
    }

    @Transactional(readOnly = true)
    fun getByEmail(email: String): UserAccount? {
        return userAccountRepository.findByEmail(email)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun hardDelete(id: Long, userId: Long) {
        // 1. 권한 확인: 해당 계정이 현재 사용자 소유인지 확인
        val userAccount = userAccountRepository.findById(id).orElseThrow {
            RuntimeException("계정을 찾을 수 없습니다.")
        }

        if (userAccount.user.id != userId) {
            throw RuntimeException("삭제 권한이 없습니다.")
        }

        // 2. OAuth Token 먼저 삭제 (FK 제약)
        oAuthTokenService.deleteByUserAccountId(id)

        // 3. UserAccount 삭제
        userAccountRepository.deleteById(id)
    }

    @Transactional
    fun disableAllByUserId(userId: Long) {
        userAccountRepository.disableAllByUserId(userId)
    }
}