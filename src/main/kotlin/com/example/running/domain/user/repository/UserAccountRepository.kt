package com.example.running.domain.user.repository

import com.example.running.domain.user.entity.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAccountRepository: JpaRepository<UserAccount, Long> {

    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): UserAccount?
}