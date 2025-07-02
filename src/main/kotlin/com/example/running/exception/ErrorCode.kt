package com.example.running.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val errorCode: String, val message: String) {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "-1", "권한이 없습니다."),
    TOKEN_IS_MALFORMED(HttpStatus.UNAUTHORIZED, "-2", "위조된 토큰입니다."),
    TOKEN_IS_EXPIRED(HttpStatus.UNAUTHORIZED, "-3", "토큰이 만료되었습니다.")
}