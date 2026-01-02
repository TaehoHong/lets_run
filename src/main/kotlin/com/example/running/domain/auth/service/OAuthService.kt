package com.example.running.domain.auth.service

import com.example.running.config.properties.Credential
import com.example.running.config.properties.CredentialProperties
import com.example.running.config.properties.OAuthProperties
import com.example.running.config.properties.OAuthUrl
import com.example.running.domain.auth.service.dto.OAuthAccountInfoDto
import com.example.running.domain.auth.service.dto.OAuthLoginResultDto
import com.example.running.domain.auth.service.dto.OAuthTokenDto
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.security.service.TokenService
import com.example.running.utils.WebClientUtils
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class OAuthService(
    private val oAuthProperties: OAuthProperties,
    private val appleClientSecretService: AppleClientSecretService,
    private val tokenService: TokenService,
    private val objectMapper: ObjectMapper
) {
    private val GRANT_TYPE = "authorization_code"
    private val log = KotlinLogging.logger {}

    /**
     * OAuth 로그인 처리
     * @return 계정 정보와 refresh token을 포함한 결과
     */
    fun getOAuthLoginResult(accountType: AccountTypeName, code: String): OAuthLoginResultDto {
        val oAuthTokenDto = requestToken(accountType, code)
        val content = tokenService.decodeTokenPayload(oAuthTokenDto.idToken)
        val accountInfo = objectMapper.readValue(content, OAuthAccountInfoDto::class.java)

        return OAuthLoginResultDto(
            accountInfo = accountInfo,
            refreshToken = oAuthTokenDto.refreshToken
        )
    }

    private fun requestToken(accountType: AccountTypeName, code: String): OAuthTokenDto {

        val (tokenUrl, _) = getURL(accountType)
        val (clientId, clientSecret) = getCredential(accountType)

        return LinkedMultiValueMap<String, String>()
            .also {
                it["client_id"] = clientId
                it["client_secret"] = clientSecret
                it["grant_type"] = GRANT_TYPE
                it["code"] = code //URLEncoder().encode(code, Charset.forName("UTF-8"))
            }.runCatching {
                WebClientUtils.postRequest(
                    url = tokenUrl,
                    queryParams = this
                )
            }.onSuccess {
                log.debug { "token response : $it" }
            }.onFailure { exception ->
                when (exception) {
                    is WebClientResponseException -> {
                        log.error { "request error occur ${exception.responseBodyAsString}" }
                        throw exception
                    }

                    else -> {
                        throw exception
                    }
                }
            }.getOrNull()
            .let {
                this.objectMapper.readValue(it, OAuthTokenDto::class.java)
            }
    }

    private fun getURL(accountType: AccountTypeName): OAuthUrl {
        return when (accountType) {
            AccountTypeName.GOOGLE -> oAuthProperties.google
            AccountTypeName.APPLE -> oAuthProperties.apple
        }
    }

    private fun getCredential(accountType: AccountTypeName): Credential {
        return when (accountType) {
            AccountTypeName.GOOGLE -> CredentialProperties.googleCredential
            AccountTypeName.APPLE -> appleClientSecretService.getCredential()
        }
    }
}