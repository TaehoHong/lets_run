package com.example.running.user.service

import com.example.running.user.entity.UserAccount
import com.example.running.user.enums.AccountTypeName
import com.example.running.user.repository.UserAccountRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserAccountService(
    val passwordEncoder: PasswordEncoder,
    val userAccountRepository: UserAccountRepository
) {

    @Transactional(readOnly = true)
    fun verifyEmailIsNotExists(email: String) {
        userAccountRepository.existsByEmail(email)
            .alsoIfTrue {
                throw RuntimeException("이미 존재하는 이메일입니다.")
            }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun save(userId: Long, email: String, password: String?) {
        userAccountRepository.save(
            UserAccount(
                userId = userId,
                accountTypeId = AccountTypeName.SELF.id,
                email = email,
                password = password?.let{ passwordEncoder.encode(it) }
            )
        )
    }

    @Transactional(readOnly = true)
    fun getByEmail(email: String): UserAccount? {
        return userAccountRepository.findByEmail(email)
    }
}