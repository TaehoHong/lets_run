package com.example.running.domain.notification.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class PushProviderResult(val ticket: String? = null, val error: String? = null, val pending: Boolean = false)
data class PushMessage(val token: String, val title: String, val body: String, val notificationId: Long)

@Component
class ExpoPushClient(
    private val mapper: ObjectMapper,
    @Value("\${notifications.expo-access-token:}") private val accessToken: String,
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun send(messages: List<PushMessage>): List<PushProviderResult> {
        if (messages.isEmpty()) return emptyList()
        require(messages.size <= 100)
        val failed = { error: String -> messages.map { PushProviderResult(error = error) } }
        return request("send", messages.map { message ->
            mapOf("to" to message.token, "title" to message.title.take(100), "body" to message.body.take(200),
                "sound" to "default", "channelId" to "activity",
                "data" to mapOf("notificationId" to message.notificationId.toString()))
        }, failed) { data ->
            // Expo tickets correspond to message order only when the complete array is valid.
            if (!data.isArray || data.size() != messages.size) failed("PROVIDER_RESPONSE")
            else data.map { ticket ->
                when {
                    ticket.path("status").asText() == "ok" && ticket.path("id").isTextual &&
                        ticket.path("id").asText().isNotBlank() -> PushProviderResult(ticket = ticket.path("id").asText())
                    ticket.path("status").asText() == "error" ->
                        PushProviderResult(error = safeError(ticket.path("details").path("error").asText()))
                    else -> PushProviderResult(error = "PROVIDER_RESPONSE")
                }
            }
        }
    }

    fun receipts(ticketIds: List<String>): Map<String, PushProviderResult> {
        if (ticketIds.isEmpty()) return emptyMap()
        require(ticketIds.size <= 100)
        val failed = { error: String -> ticketIds.associateWith { PushProviderResult(error = error) } }
        return request("getReceipts", mapOf("ids" to ticketIds), failed) { data ->
            if (!data.isObject) failed("PROVIDER_RESPONSE")
            else ticketIds.associateWith { id ->
                val receipt = data.get(id)
                when {
                    receipt == null -> PushProviderResult(pending = true)
                    receipt.path("status").asText() == "ok" -> PushProviderResult()
                    receipt.path("status").asText() == "error" ->
                        PushProviderResult(error = safeError(receipt.path("details").path("error").asText()))
                    else -> PushProviderResult(error = "PROVIDER_RESPONSE")
                }
            }
        }
    }

    private fun <T> request(path: String, body: Any, failed: (String) -> T, decode: (JsonNode) -> T): T {
        try {
            val builder = HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/$path"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            if (accessToken.isNotBlank()) builder.header("Authorization", "Bearer $accessToken")
            val response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString())
            return when (response.statusCode()) {
                200 -> {
                    val root = mapper.readTree(response.body())
                    if (root == null || !root.isObject || !root.hasNonNull("data")) failed("PROVIDER_RESPONSE")
                    else decode(root.path("data"))
                }
                429 -> failed("MessageRateExceeded")
                401, 403 -> failed("InvalidCredentials")
                else -> failed("PROVIDER_UNAVAILABLE")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return failed("INTERRUPTED")
        } catch (_: java.io.IOException) {
            // Provider response bodies and device tokens must never be logged or persisted.
            return failed("PROVIDER_UNAVAILABLE")
        }
    }

    private fun safeError(code: String?): String = when (code) {
        "DeviceNotRegistered", "MessageTooBig", "MessageRateExceeded", "MismatchSenderId", "InvalidCredentials" -> code
        else -> "PROVIDER_RESPONSE"
    }
}
