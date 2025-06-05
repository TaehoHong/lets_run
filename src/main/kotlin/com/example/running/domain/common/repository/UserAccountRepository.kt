package com.example.running.domain.common.repository

import com.example.running.domain.common.entity.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAccountRepository: JpaRepository<UserAccount, Long> {

    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): UserAccount?
}