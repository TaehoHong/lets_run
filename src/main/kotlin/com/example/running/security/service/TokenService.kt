package com.example.running.security.service

import com.example.running.config.properties.JwtProperties
import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.user_auth.controller.dto.TokenResponse
import com.example.running.exception.ErrorCode
import com.example.running.security.exception.TokenException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.impl.Base64UrlCodec
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.token.Sha512DigestUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.util.*

@Service
class TokenService {

    private val log = KotlinLogging.logger {  }

    @Value(value = "\${token.expiration.access}")
    var ACCESS_TOKEN_EXPIRATION : Long = 14400

    @Value(value = "\${token.expiration.refresh}")
    var REFRESH_TOKEN_EXPIRATION : Long = 144000

    val secret = "running_app_token_secret"


    @Transactional
    fun generateTokens(userId: Long, nickname: String, email: String, authorityType: AuthorityType): TokenResponse {
        return TokenResponse(
            userId = userId,
            nickname = nickname,
            accessToken = generateAccessToken(userId, email, authorityType),
            refreshToken = generateRefreshToken(userId, email, authorityType)
        )
    }


    fun generateAccessToken(userId: Long, email: String, authorityType: AuthorityType) =
        generateToken(userId, email, authorityType, ACCESS_TOKEN_EXPIRATION)

    fun generateRefreshToken(userId: Long, email: String, authorityType: AuthorityType) =
        generateToken(userId, email, authorityType, REFRESH_TOKEN_EXPIRATION)



    private fun generateToken(userId: Long, email: String, authorityType: AuthorityType, tokenExpirationTime: Long): String {

        val claims = createClaims(userId, email, authorityType)

        val now = Date()
        val expiredDate = Date(now.time + (tokenExpirationTime * 1000))

        return Jwts.builder()
            .claims(claims)
            .issuedAt(now)
            .expiration(expiredDate)
            .signWith(getSecretKey())
            .compact()
    }


    private fun createClaims(userId: Long, email: String, authorityType: AuthorityType) =
        Jwts.claims()
            .id(userId.toString())
            .subject(email)
            .also {
                it.add("role", authorityType.role)
            }.build()

    fun decodeTokenPayload(token: String): String? {
        return token.split(".")[1]
            .let {
                log.info{"payload: $it"}
                Base64UrlCodec().decodeToString(it)
            }
    }

    private fun getSecretKey() = Keys.hmacShaKeyFor(
        Base64.getEncoder().encode(Sha512DigestUtils.sha(this.secret))
    )


    @Throws(IOException::class)
    fun verifyToken(tokenHeader: String): Boolean {
        if (!tokenHeader.startsWith(JwtProperties.TOKEN_PREFIX)) {
            throw TokenException(ErrorCode.UNAUTHORIZED)
        } else {
            runCatching {
                val token = tokenHeader.replace(JwtProperties.TOKEN_PREFIX, "")
                    .also { log.debug{"token=$it"} }

                val payload = getClaims(token).payload

                return true

            }.onFailure { exception ->

                when(exception) {
                    is MalformedJwtException -> {
                        log.error {"JWT token is malformed"}
                        ErrorCode.TOKEN_IS_MALFORMED
                    }
                    is SignatureException -> {
                        log.error {"Unable to get JWT token"}
                        ErrorCode.UNAUTHORIZED
                    }
                    is ExpiredJwtException -> {
                        log.error {"JWT token has expired"}
                        ErrorCode.TOKEN_IS_EXPIRED
                    }
                    else -> {
                        log.error("error: {}", exception.message)
                        ErrorCode.UNAUTHORIZED
                    }
                }.run {
                    throw TokenException(this)
                }
            }
        }

        return false
    }

    fun getId(token: String): String {
        val payload: Claims = getClaims(token).payload as Claims

        return payload.id
    }

    fun getEmail(token: String): String {
        val payload: Claims = getClaims(token).payload as Claims

        return payload.subject
    }

    fun getAuthorityType(token: String): AuthorityType {
        val payload: Claims = getClaims(token).payload as Claims

        return payload.get("role")
            .let {
                AuthorityType.get(it as String)
            }
    }

    private fun getClaims(token: String) = Jwts.parser().verifyWith(getSecretKey()).build().parse(token)
}
