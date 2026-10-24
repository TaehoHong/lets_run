package com.example.running.domain.notification.controller

import com.example.running.domain.notification.repository.InboxNotification
import com.example.running.domain.notification.repository.NotificationConfiguration
import com.example.running.domain.notification.repository.NotificationText
import com.example.running.domain.notification.repository.NotificationType
import com.example.running.domain.notification.service.NotificationService
import com.example.running.helper.authenticateWithUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

data class DeviceRequest(
    @field:Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") val installationId: String,
    @field:Size(max = 255) @field:Pattern(regexp = "(ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]+]") val pushToken: String?,
    @field:Pattern(regexp = "IOS|ANDROID") val platform: String,
    @field:Size(min = 2, max = 64) val language: String,
)
data class DeviceUnbindRequest(val installationId: String, @field:PositiveOrZero val version: Long)
data class TimezoneRequest(val timezone: String)
data class NoticeRequest(@field:Size(min = 1, max = 100) val translations: List<NotificationText>)
data class InboxPage(val content: List<InboxNotification>, val cursor: Long?, val hasNext: Boolean)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val service: NotificationService) {
    @GetMapping
    fun inbox(@RequestParam(defaultValue = "ko") language: String,
              @RequestParam(required = false) cursor: Long?, @RequestParam(defaultValue = "20") size: Int,
              @RequestParam(required = false) type: NotificationType?): InboxPage =
        authenticateWithUser { userId ->
            val items = service.inbox(userId, language, cursor, size, type)
            val page = items.take(size)
            InboxPage(page, page.lastOrNull()?.id, items.size > size)
        }

    @PostMapping("/{id}/open")
    fun open(@PathVariable id: Long, @RequestParam(defaultValue = "ko") language: String) =
        authenticateWithUser { service.open(it, id, language) }

    @GetMapping("/configuration")
    fun configuration() = authenticateWithUser { service.configuration(it) }

    @PutMapping("/configuration")
    fun configure(@RequestBody value: NotificationConfiguration) = authenticateWithUser { service.configure(it, value) }

    @PatchMapping("/configuration/timezone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateTimezone(@RequestBody value: TimezoneRequest) = authenticateWithUser {
        service.updateTimezone(it, value.timezone)
    }

    @PutMapping("/device")
    fun bind(@Valid @RequestBody request: DeviceRequest) = authenticateWithUser {
        service.bindDevice(it, request.installationId, request.pushToken, request.platform, request.language)
    }

    @PostMapping("/device/unbind")
    fun unbind(@Valid @RequestBody request: DeviceUnbindRequest) = authenticateWithUser {
        service.unbindDevice(it, request.installationId, request.version)
    }
}

@RestController
@RequestMapping("/api/v1/admin/notices")
class NoticeController(private val service: NotificationService) {
    @PostMapping
    fun create(@Valid @RequestBody request: NoticeRequest) = authenticateWithUser {
        mapOf("id" to service.createNotice(it, request.translations))
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long) = authenticateWithUser { service.publishNotice(it, id) }
}

@RestControllerAdvice(assignableTypes = [NotificationController::class, NoticeController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationControllerAdvice {
    @ExceptionHandler(DataAccessException::class)
    fun databaseError(error: DataAccessException): ResponseEntity<Map<String, String>> {
        LoggerFactory.getLogger(javaClass).warn("Notification storage failed ({})", error.javaClass.simpleName)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("message" to "Notification storage unavailable"))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun malformedRequest(): ResponseEntity<Map<String, String>> = ResponseEntity.badRequest()
        .body(mapOf("message" to "Invalid notification request"))
}
