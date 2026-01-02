package com.example.running.domain.auth.service

import com.example.running.utils.WebClientUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class AppleTokenRevokeService(
    private val appleClientSecretService: AppleClientSecretService
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke"
    }

    /**
     * Apple refresh token 해제
     * @param refreshToken 해제할 refresh token
     * @return 성공 여부
     */
    fun revokeToken(refreshToken: String): Boolean {
        val credential = appleClientSecretService.getCredential()

        val params = LinkedMultiValueMap<String, String>().apply {
            add("client_id", credential.clientId)
            add("client_secret", credential.clientSecret)
            add("token", refreshToken)
            add("token_type_hint", "refresh_token")
        }

        return runCatching {
            WebClientUtils.postRequest(
                url = APPLE_REVOKE_URL,
                queryParams = params
            )
        }.onSuccess {
            log.info { "Successfully revoked Apple token" }
        }.onFailure { exception ->
            when (exception) {
                is WebClientResponseException -> {
                    log.error { "Failed to revoke Apple token: ${exception.responseBodyAsString}" }
                }
                else -> {
                    log.error { "Failed to revoke Apple token: ${exception.message}" }
                }
            }
        }.isSuccess
    }
}
