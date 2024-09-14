package com.example.running.security.exception

import com.example.running.enums.ErrorCode

data class TokenException(
    val errorCode: String,
    override val message: String
) : RuntimeException(message) {

    constructor(errorCode: ErrorCode): this(
        errorCode.errorCode,
        errorCode.message
    )
}