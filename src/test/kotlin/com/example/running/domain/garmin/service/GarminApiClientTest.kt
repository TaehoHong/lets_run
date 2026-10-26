package com.example.running.domain.garmin.service

import com.example.running.config.properties.GarminProperties
import com.example.running.exception.GarminApiException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import kotlin.test.*

class GarminApiClientTest {
    private val http = mock(HttpClient::class.java)
    private val client = GarminApiClient(GarminProperties(clientId = "test-client", clientSecret = "test-secret"),
        jacksonObjectMapper(), http)

    @Suppress("UNCHECKED_CAST")
    private fun reply(status: Int = 200, body: String = """{"access_token":"access-test","refresh_token":"rotated-refresh-test","expires_in":86400,"refresh_token_expires_in":7776000}""") {
        val response = mock(HttpResponse::class.java) as HttpResponse<String>
        `when`(response.statusCode()).thenReturn(status)
        `when`(response.body()).thenReturn(body)
        `when`(http.send(any(HttpRequest::class.java), any<HttpResponse.BodyHandler<String>>())).thenReturn(response)
    }

    private fun request(): HttpRequest {
        val capture = ArgumentCaptor.forClass(HttpRequest::class.java)
        verify(http).send(capture.capture(), any<HttpResponse.BodyHandler<String>>())
        return capture.value
    }

    @Test
    fun `authorization code and verifier are form encoded and the secret only goes to the token endpoint`() {
        reply()
        val tokens = client.exchangeCode("code&+ test", "verifier-test")
        val request = request()
        assertEquals("https://diauth.garmin.com/di-oauth2-service/oauth/token", request.uri().toString())
        assertEquals("POST", request.method())
        assertEquals("Basic " + Base64.getEncoder().encodeToString("test-client:test-secret".toByteArray()),
            request.headers().firstValue("Authorization").orElseThrow())
        val body = body(request)
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=code%26%2B+test"))
        assertTrue(body.contains("code_verifier=verifier-test"))
        assertFalse(request.uri().toString().contains("test-secret"))
        assertEquals("rotated-refresh-test", tokens.refreshToken)
    }

    @Test
    fun `refresh uses the latest refresh token and retains the rotated replacement`() {
        reply()
        assertEquals("rotated-refresh-test", client.refresh("latest-refresh-test").refreshToken)
        assertTrue(body(request()).contains("grant_type=refresh_token&refresh_token=latest-refresh-test"))
    }

    @Test
    fun `provider error bodies never enter exception messages or causes`() {
        reply(400, "private-provider-response-with-a-token")
        val failure = assertFailsWith<GarminApiException> { client.refresh("private-refresh-test") }
        assertEquals(400, failure.statusCode)
        assertFalse(failure.message.orEmpty().contains("private"))
        assertNull(failure.cause)
    }

    @Test
    fun `user identity is fetched from the OAuth2 partner gateway with the user access token`() {
        reply(body = """{"userId":"garmin-user-test"}""")
        assertEquals("garmin-user-test", client.userId("access-test"))
        val request = request()
        assertEquals("https://apis.garmin.com/partner-gateway/rest/user/id", request.uri().toString())
        assertEquals("Bearer access-test", request.headers().firstValue("Authorization").orElseThrow())
    }

    @Test
    fun `invalid token JSON is sanitized rather than exposing the response`() {
        reply(body = "private-token-is-not-json")
        val failure = assertFailsWith<GarminApiException> { client.refresh("refresh-test") }
        assertEquals(502, failure.statusCode)
        assertFalse(failure.message.orEmpty().contains("private"))
        assertNull(failure.cause)
    }

    @Test
    fun `refresh invalid grant is classified without retaining provider details`() {
        reply(400, """{"error":"invalid_grant","error_description":"private-refresh-token"}""")

        val failure = assertFailsWith<GarminApiException> { client.refresh("private-refresh-test") }

        assertEquals(400, failure.statusCode)
        assertTrue(failure.credentialsInvalid)
        assertEquals("Garmin request failed", failure.message)
        assertNull(failure.cause)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        """{"error":"invalid_client"}""",
        """{"error":"invalid_request"}""",
        """{"error":"unknown"}""",
        """{"error":["invalid_grant"]}""",
        """{"error_description":"invalid_grant"}""",
        "private-not-json",
        """{"error":"invalid_grant"} private-trailing-content""",
    ])
    fun `client and unknown or malformed refresh errors never invalidate credentials`(body: String) {
        reply(400, body)

        val failure = assertFailsWith<GarminApiException> { client.refresh("refresh-test") }

        assertEquals(400, failure.statusCode)
        assertFalse(failure.credentialsInvalid)
        assertEquals("Garmin request failed", failure.message)
        assertNull(failure.cause)
    }

    @Test
    fun `invalid grant outside refresh HTTP 400 never invalidates credentials`() {
        reply(401, """{"error":"invalid_grant"}""")
        assertFalse(assertFailsWith<GarminApiException> { client.refresh("refresh-test") }.credentialsInvalid)
        reply(400, """{"error":"invalid_grant"}""")
        assertFalse(assertFailsWith<GarminApiException> { client.exchangeCode("code", "verifier") }.credentialsInvalid)
        assertFalse(assertFailsWith<GarminApiException> { client.deregister("access-test") }.credentialsInvalid)
    }

    @ParameterizedTest
    @ValueSource(ints = [404, 410])
    fun `already absent remote registration is an idempotent success`(status: Int) {
        reply(status)
        client.deregister("access-test")
    }

    private fun body(request: HttpRequest): String {
        val output = ByteArrayOutputStream()
        val done = CompletableFuture<Unit>()
        request.bodyPublisher().orElseThrow().subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)
            override fun onNext(buffer: ByteBuffer) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                output.write(bytes)
            }
            override fun onError(error: Throwable) { done.completeExceptionally(error) }
            override fun onComplete() { done.complete(Unit) }
        })
        done.join()
        return output.toString(Charsets.UTF_8)
    }
}
