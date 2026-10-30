package com.example.running.exception

import com.fasterxml.jackson.annotation.JsonInclude

data class ErrorResponse (
    val message: String,
    val extra: Map<String, String>? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val code: String? = null
) {
    constructor(apiError: ApiError, extra: Map<String, String>): this(
        message = apiError.message,
        extra = extra
    )
}
