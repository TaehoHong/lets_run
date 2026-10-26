package com.example.running.domain.garmin.controller

import com.example.running.domain.garmin.service.GarminWebhookReceiver
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GarminWebhookController(private val receiver: GarminWebhookReceiver) {
    @PostMapping("/api/v1/garmin/webhook", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun receive(@RequestParam(required = false) token: String?, request: HttpServletRequest): ResponseEntity<Void> {
        receiver.receive(token, request.inputStream)
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").build()
    }
}
