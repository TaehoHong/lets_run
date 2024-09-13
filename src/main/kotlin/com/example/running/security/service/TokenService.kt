package com.example.running.security.service

import com.example.running.user.controller.dto.TokenResponse
import com.example.running.user.enums.AuthorityType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.impl.Base64UrlCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.token.Sha512DigestUtils
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.Date

@Service
class TokenService {

    private val log = KotlinLogging.logger {  }

    @Value(value = "\${token.expiration.access}")
    var ACCESS_TOKEN_EXPIRATION : Long = 14400

    @Value(value = "\${token.expiration.refresh}")
    var REFRESH_TOKEN_EXPIRATION : Long = 144000

    val secret = "running_app_token_secret"

    fun generateTokens(userId: Long, email: String, authorityType: AuthorityType): TokenResponse {
        return TokenResponse(
            userId = userId,
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
        val expiredDate = Date(now.time + tokenExpirationTime)

        return Jwts.builder()
            .claims(claims)
            .issuedAt(now)
            .expiration(expiredDate)
            .signWith(SignatureAlgorithm.HS512, getSecretKey())
            .compact()
    }


    private fun createClaims(userId: Long, email: String, authorityType: AuthorityType) =
        Jwts.claims()
            .id(userId.toString())
            .subject(email)
            .also {
                it.add("role", "ROLE_" + authorityType.name)
            }.build()

    fun decodeTokenPayload(token: String): String? {
        return token.split(".")[1]
            .let {
                log.info{"payload: $it"}
                Base64UrlCodec().decodeToString(it)
            }
    }

    private fun getSecretKey() = Base64.getEncoder().encodeToString(
        Sha512DigestUtils.sha(this.secret)
    )


//    @Throws(IOException::class)
//    fun verifyToken(tokenHeader: String): Boolean {
//        if (!tokenHeader.startsWith("Bearer ")) {
//            throw new TokenException(ErrorCode.UNAUTHORIZED, "토큰이 존재하지 않거나 잘못된 토큰입니다.");
//        } else {
//            try {
//                String token = tokenHeader.replace(JwtProperties.TOKEN_PREFIX, "");
//                log.info("token={}", token);
//                Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
//                return claims.getBody().getExpiration().after(new Date());
//            } catch (MalformedJwtException e) {
//                log.error("JWT token is malformed");
//                throw new TokenException(ErrorCode.COUNTERFEIT, "위조된 토큰입니다.");
//            } catch (SignatureException e) {
//                log.error("Unable to get JWT token", e);
//                throw new TokenException(ErrorCode.UNAUTHORIZED, "유효하지 않는 토큰");
//            }catch (ExpiredJwtException e) {
//                log.error("JWT token has expired", e);
//                return false;
//            }
//        }
//    }
}