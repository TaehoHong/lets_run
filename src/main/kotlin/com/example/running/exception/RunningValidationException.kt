package com.example.running.exception

enum class RunningValidationReason(val safeMessage: String) {
    RUNNING_FUTURE_END_TIME("Running end time is in the future"),
    RUNNING_EXCESSIVE_AVERAGE_SPEED("Running average speed exceeds the allowed limit"),
    RUNNING_INVALID_METRICS("Invalid running metrics"),
    RUNNING_INVALID_GPS("Invalid running GPS data"),
}

class RunningValidationException(val reason: RunningValidationReason) : RuntimeException(reason.safeMessage) {
    fun response() = ErrorResponse(reason.safeMessage, mapOf("code" to reason.name))
}
