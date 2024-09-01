package com.example.running.security.service

import com.example.running.user.enums.AuthorityType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ClaimsBuilder
import io.jsonwebtoken.Jwt
import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.Date

@Service
class TokenService {

    @Value(value = "\${token.expiration.access}")
    var ACCESS_TOKEN_EXPIRATION : Long = 14400

    @Value(value = "\${token.expiration.refresh}")
    var REFRESH_TOKEN_EXPIRATION : Long = 144000


    fun generateAccessToken(userId: Long, email: String, authorityType: AuthorityType) =
        generateToken(userId, email, authorityType, ACCESS_TOKEN_EXPIRATION)

    fun generateRefreshToken(userId: Long, email: String, authorityType: AuthorityType) =
        generateToken(userId, email, authorityType, REFRESH_TOKEN_EXPIRATION)



    private fun generateToken(userId: Long, email: String, authorityType: AuthorityType, tokenExpirationTime: Long): String {

        val claims = createClaims(userId, email, authorityType)

        val now = Date()
        val expiredDate = Date(now.time + tokenExpirationTime)

        return Jwts.builder()
            .claims(claims)
            .issuedAt(now)
            .expiration(expiredDate)
            .compact()
    }


    private fun createClaims(userId: Long, email: String, authorityType: AuthorityType) =
        Jwts.claims()
            .id(userId.toString())
            .subject(email)
            .build()
            .also {
                it["role"] = "ROLE_" + authorityType.name
            }
}