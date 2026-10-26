package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.domain.garmin.service.dto.GarminTokens
import com.example.running.exception.GarminApiException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

@Component
class GarminApiClient internal constructor(
    private val properties: GarminProperties,
    private val mapper: ObjectMapper,
    private val http: HttpClient,
) {
    @Autowired
    constructor(properties: GarminProperties, mapper: ObjectMapper) : this(
        properties,
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
    )

    fun authorizationUrl(state: String, challenge: String): String =
        "https://connect.garmin.com/oauth2Confirm?" + form(
            linkedMapOf(
                "client_id" to properties.clientId,
                "response_type" to "code",
                "state" to state,
                "redirect_uri" to properties.redirectUri,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            )
        )

    fun exchangeCode(code: String, verifier: String): GarminTokens = token(
        linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to properties.redirectUri,
        )
    )

    fun refresh(refreshToken: String): GarminTokens = token(
        linkedMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
    )

    fun userId(accessToken: String): String {
        val id = json(exchange(partnerRequest("id", accessToken).GET().build())).path("userId").asText()
        if (id.isBlank() || id.length > 255) throw GarminApiException(502)
        return id
    }

    fun permissions(accessToken: String): Set<String> {
        val values = json(exchange(partnerRequest("permissions", accessToken).GET().build())).path("permissions")
        if (!values.isArray || values.any { !it.isTextual }) throw GarminApiException(502)
        return values.map { it.asText() }.toSet()
    }

    fun deregister(accessToken: String) {
        try {
            exchange(partnerRequest("registration", accessToken).DELETE().build())
        } catch (error: GarminApiException) {
            if (error.statusCode != 404 && error.statusCode != 410) throw error
        }
    }

    private fun token(parameters: Map<String, String>): GarminTokens {
        val credentials = Base64.getEncoder().encodeToString(
            "${properties.clientId}:${properties.clientSecret}".toByteArray(Charsets.UTF_8)
        )
        val request = HttpRequest.newBuilder(URI.create("https://diauth.garmin.com/di-oauth2-service/oauth/token"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic $credentials")
            .POST(HttpRequest.BodyPublishers.ofString(form(parameters)))
            .build()
        val result = json(exchange(request, refreshGrant = parameters["grant_type"] == "refresh_token"))
        val access = result.path("access_token").asText()
        val refresh = result.path("refresh_token").asText()
        val expires = result.path("expires_in").asLong()
        val refreshExpiry = result.path("refresh_token_expires_in")
        val refreshExpires = if (refreshExpiry.isMissingNode || refreshExpiry.isNull) null else refreshExpiry.asLong()
        if (access.isBlank() || refresh.isBlank() || expires <= 0 || (refreshExpires != null && refreshExpires <= 0) ||
            !result.path("token_type").asText("bearer").equals("bearer", ignoreCase = true)) {
            throw GarminApiException(502)
        }
        return GarminTokens(access, refresh, expires, refreshExpires)
    }

    private fun partnerRequest(resource: String, accessToken: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("https://apis.garmin.com/partner-gateway/rest/user/$resource"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer $accessToken")

    private fun exchange(request: HttpRequest, refreshGrant: Boolean = false): String {
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GarminApiException(503)
        } catch (_: IOException) {
            throw GarminApiException(503)
        }
        if (response.statusCode() !in 200..299) {
            val credentialsInvalid = if (refreshGrant && response.statusCode() == 400) {
                try {
                    val body: JsonNode? = mapper.readerFor(JsonNode::class.java)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readValue(response.body())
                    val error = body?.path("error")
                    error?.isTextual == true && error.asText() == "invalid_grant"
                } catch (_: JsonProcessingException) {
                    false
                }
            } else false
            throw GarminApiException(response.statusCode(), credentialsInvalid)
        }
        return response.body()
    }

    private fun json(body: String): JsonNode = try {
        mapper.readTree(body) ?: throw GarminApiException(502)
    } catch (_: JsonProcessingException) {
        throw GarminApiException(502)
    }

    private fun form(parameters: Map<String, String>): String = parameters.entries.joinToString("&") {
        URLEncoder.encode(it.key, Charsets.UTF_8) + "=" + URLEncoder.encode(it.value, Charsets.UTF_8)
    }
}
