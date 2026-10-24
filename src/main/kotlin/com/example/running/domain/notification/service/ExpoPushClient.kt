package com.example.running.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class PushProviderResult(val ticket: String? = null, val error: String? = null, val pending: Boolean = false)

@Component
class ExpoPushClient(
    private val mapper: ObjectMapper,
    @Value("\${notifications.expo-access-token:}") private val accessToken: String,
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun send(token: String, title: String, body: String, notificationId: Long): PushProviderResult = request(
        "send", mapOf("to" to token, "title" to title.take(100), "body" to body.take(200), "sound" to "default",
            "channelId" to "activity", "data" to mapOf("notificationId" to notificationId.toString()))
    ) { data ->
        val ticket = if (data.isArray) data.firstOrNull() else data
        when {
            ticket?.path("status")?.asText() == "ok" && !ticket.path("id").asText().isNullOrBlank() ->
                PushProviderResult(ticket = ticket.path("id").asText())
            else -> PushProviderResult(error = safeError(ticket?.path("details")?.path("error")?.asText()))
        }
    }

    fun receipt(ticketId: String): PushProviderResult = request("getReceipts", mapOf("ids" to listOf(ticketId))) { data ->
        val receipt = data.get(ticketId)
        when {
            receipt == null -> PushProviderResult(pending = true)
            receipt.path("status").asText() == "ok" -> PushProviderResult()
            else -> PushProviderResult(error = safeError(receipt.path("details").path("error").asText()))
        }
    }

    private fun request(path: String, body: Any, decode: (com.fasterxml.jackson.databind.JsonNode) -> PushProviderResult): PushProviderResult {
        try {
            val builder = HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/$path"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            if (accessToken.isNotBlank()) builder.header("Authorization", "Bearer $accessToken")
            val response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString())
            return when (response.statusCode()) {
                200 -> {
                    val root = mapper.readTree(response.body())
                    if (!root.has("data")) PushProviderResult(error = "PROVIDER_RESPONSE") else decode(root.path("data"))
                }
                429 -> PushProviderResult(error = "MessageRateExceeded")
                401, 403 -> PushProviderResult(error = "InvalidCredentials")
                else -> PushProviderResult(error = "PROVIDER_UNAVAILABLE")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return PushProviderResult(error = "INTERRUPTED")
        } catch (_: java.io.IOException) {
            // Provider response bodies and device tokens must never be logged or persisted.
            return PushProviderResult(error = "PROVIDER_UNAVAILABLE")
        }
    }

    private fun safeError(code: String?): String = when (code) {
        "DeviceNotRegistered", "MessageTooBig", "MessageRateExceeded", "MismatchSenderId", "InvalidCredentials" -> code
        else -> "PROVIDER_RESPONSE"
    }
}
