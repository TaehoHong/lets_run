package com.example.running.domain.auth.service

import com.example.running.config.properties.CredentialProperties
import com.example.running.domain.auth.service.dto.GoogleTokenDto
import com.example.running.utils.WebClientUtils
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class GoogleOauthService(private val objectMapper: ObjectMapper) {

    private val log = KotlinLogging.logger {}

    @Value("\${oauth.google.token_url}")
    private var tokenUrl : String? = null

    @Value("\${oauth.google.redirect_url}")
    private var redirectUrl: String? = null

    @Value("\${oauth.google.grant_type}")
    private var grantType: String? = null

    fun requestToken(code: String): GoogleTokenDto {

        assert(tokenUrl != null)

        return LinkedMultiValueMap<String, String>()
            .also {
                it["client_id"] = CredentialProperties.googleClientId
                it["client_secret"] = CredentialProperties.googleClientSecret
                it["redirect_uri"] = this.redirectUrl
                it["grant_type"] = this.grantType
                it["code"] = code //URLEncoder().encode(code, Charset.forName("UTF-8"))
            }.runCatching {
                val postRequest = WebClientUtils.postRequest(
                    url = tokenUrl!!,
                    queryParams = this
                )
                postRequest
            }.onSuccess {
                log.debug { "google token response : $it" }
            }.onFailure { exception ->
                when(exception) {
                    is WebClientResponseException -> {
                        log.error { "request error occur ${exception.responseBodyAsString}" }
                        throw exception
                    }
                    else -> { throw exception }
                }
            }.getOrNull()
            .let {
                this.objectMapper.readValue(it, GoogleTokenDto::class.java)
            }

    }

//    fun getUserInfo(googleTokenDto: GoogleTokenDto): OauthAccountInfo {
//
//    }
}