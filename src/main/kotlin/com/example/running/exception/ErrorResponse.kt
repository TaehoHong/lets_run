package com.example.running.exception

data class ErrorResponse (
    val message: String,
    val extra: Map<String, String>? = null
) {
    constructor(apiError: ApiError, extra: Map<String, String>): this(
        message = apiError.message,
        extra = extra
    )
}