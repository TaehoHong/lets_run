package com.example.running.user.service

import com.example.running.user.repository.UserAccountRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class UserAccountService(
    val userAccountRepository: UserAccountRepository
) {

    @Transactional(readOnly = true)
    fun verifyEmailIsNotExists(email: String) {
        userAccountRepository.existsByEmail(email)
            .alsoIfTrue {
                throw RuntimeException("이미 존재하는 이메일입니다.")
            }
    }
}