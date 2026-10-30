package com.example.running.exception

import org.springframework.http.HttpStatus

class ApiException (
    message: String,
    val status : HttpStatus,
    val code: String? = null
) : RuntimeException(message) {

    constructor(apiError: ApiError) : this(
        message = apiError.message,
        status = apiError.status,
        code = apiError.code
    )
}
