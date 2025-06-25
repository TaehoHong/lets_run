package com.example.running.security.exception

import com.example.running.enums.ErrorCode
import org.springframework.security.core.AuthenticationException

data class TokenException(
    val errorCode: String,
    override val message: String
) : AuthenticationException(message) {

    constructor(errorCode: ErrorCode): this(
        errorCode.errorCode,
        errorCode.message
    )
}