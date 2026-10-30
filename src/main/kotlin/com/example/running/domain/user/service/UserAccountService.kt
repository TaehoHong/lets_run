package com.example.running.domain.user.service

import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.domain.user.repository.UserRepository
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import com.example.running.utils.alsoIfTrue
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserAccountService(
    private val passwordEncoder: PasswordEncoder,
    private val userAccountRepository: UserAccountRepository,
    private val oAuthTokenService: OAuthTokenService,
    private val userRepository: UserRepository
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
    fun getByEmailAndAccountType(email: String, accountType: AccountTypeName): UserAccount? {
        return userAccountRepository.findByEmailAndAccountTypeIdAndIsDeletedFalse(email, accountType.id)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun hardDelete(id: Long, userId: Long) {
        // 사용자 행으로 연결 해제를 직렬화하고, 잠금 조회로 최신 계정 상태를 확인한다.
        userRepository.findByIdForUpdate(userId) ?: throw ApiException(ApiError.NOT_FOUND_USER)
        val accounts = userAccountRepository.findAllByUserIdForUpdate(userId)
        val userAccount = accounts.firstOrNull { it.id == id }
            ?: throw ApiException(ApiError.NOT_FOUND_USER_ACCOUNT)

        if (userAccount.isEnabled && !userAccount.isDeleted &&
            accounts.count { it.isEnabled && !it.isDeleted } <= 1) {
            throw ApiException(ApiError.LAST_ACCOUNT_CANNOT_DISCONNECT)
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
