package com.example.running.domain.user.service

import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.user.entity.UserAccount
import com.example.running.domain.user.repository.UserAccountRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserAccountService(
    private val passwordEncoder: PasswordEncoder,
    private val userAccountRepository: UserAccountRepository
) {

    @Transactional(readOnly = true)
    fun verifyEmailIsNotExists(email: String) {
        userAccountRepository.existsByEmail(email)
            .alsoIfTrue {
                throw RuntimeException("이미 존재하는 이메일입니다.")
            }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun save(userId: Long, email: String, password: String? = null, accountType: AccountTypeName): UserAccount {
        return userAccountRepository.save(
            UserAccount(
                userId = userId,
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
    fun softDelete(id: Long, userId: Long) {
        userAccountRepository.updateIsDeleted(true, id, userId)
    }
}