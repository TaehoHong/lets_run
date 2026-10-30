package com.example.running.domain.garmin.controller

import com.example.running.domain.garmin.controller.dto.GarminAuthorizationRequest
import com.example.running.domain.garmin.service.GarminConnectionService
import com.example.running.helper.authenticateWithUser
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/garmin")
class GarminController(
    private val connections: GarminConnectionService,
) {
    @ModelAttribute
    fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
    }

    @GetMapping("/connection")
    fun connection() = authenticateWithUser { connections.connection(it) }

    @PostMapping("/authorization")
    fun authorize(@RequestBody(required = false) request: GarminAuthorizationRequest?) =
        authenticateWithUser { connections.authorize(it, request?.appScheme) }

    @DeleteMapping("/connection")
    fun disconnect() = authenticateWithUser { connections.disconnect(it) }

    @GetMapping("/callback")
    fun callback(
        @RequestParam state: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, connections.callback(state, code, error))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Referrer-Policy", "no-referrer")
            .build()
}
