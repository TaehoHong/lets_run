package com.example.running.domain.auth.service

import com.example.running.domain.auth.entity.OAuthToken
import com.example.running.domain.auth.repository.OAuthTokenRepository
import com.example.running.domain.user.entity.UserAccount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuthTokenService(
    private val oAuthTokenRepository: OAuthTokenRepository
) {
    private val log = KotlinLogging.logger {}

    /**
     * OAuth 토큰 저장 또는 업데이트
     */
    @Transactional(rollbackFor = [Exception::class])
    fun saveOrUpdate(userAccount: UserAccount, refreshToken: String?): OAuthToken {
        val existingToken = oAuthTokenRepository.findByUserAccountId(userAccount.id)

        return if (existingToken != null) {
            existingToken.updateRefreshToken(refreshToken)
            log.debug { "Updated OAuth token for userAccountId: ${userAccount.id}" }
            existingToken
        } else {
            val newToken = OAuthToken(
                userAccount = userAccount,
                refreshToken = refreshToken
            )
            log.debug { "Created new OAuth token for userAccountId: ${userAccount.id}" }
            oAuthTokenRepository.save(newToken)
        }
    }

    /**
     * 사용자 ID로 모든 OAuth 토큰 조회
     */
    @Transactional(readOnly = true)
    fun findAllByUserId(userId: Long): List<OAuthToken> {
        return oAuthTokenRepository.findAllByUserId(userId)
    }

    /**
     * UserAccount ID로 OAuth 토큰 조회
     */
    @Transactional(readOnly = true)
    fun findByUserAccountId(userAccountId: Long): OAuthToken? {
        return oAuthTokenRepository.findByUserAccountId(userAccountId)
    }

    /**
     * 사용자 ID로 모든 OAuth 토큰 삭제
     */
    @Transactional
    fun deleteAllByUserId(userId: Long) {
        oAuthTokenRepository.deleteAllByUserId(userId)
        log.debug { "Deleted all OAuth tokens for userId: $userId" }
    }
}
