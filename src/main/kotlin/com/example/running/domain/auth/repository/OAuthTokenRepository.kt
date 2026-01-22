package com.example.running.domain.auth.repository

import com.example.running.domain.auth.entity.OAuthToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OAuthTokenRepository : JpaRepository<OAuthToken, Long> {

    fun findByUserAccountId(userAccountId: Long): OAuthToken?

    @Query("SELECT o FROM OAuthToken o WHERE o.userAccount.user.id = :userId")
    fun findAllByUserId(userId: Long): List<OAuthToken>

    @Modifying
    @Query("DELETE FROM OAuthToken o WHERE o.userAccount.user.id = :userId")
    fun deleteAllByUserId(userId: Long)

    fun deleteByUserAccountId(userAccountId: Long)
}
