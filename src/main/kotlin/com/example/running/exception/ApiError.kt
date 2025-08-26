package com.example.running.exception

import org.springframework.http.HttpStatus

enum class ApiError(val status: HttpStatus, val message: String) {


    //Auth
    NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "Not authorized"),

    BAD_REQUEST_FIELD_VALID_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    NOT_FOUND_USER_ACCOUNT(HttpStatus.NOT_FOUND, "유저 계정이 존재하지 않습니다."),

    // User
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "Not Found User"),


    // Shoe
    NOT_FOUND_SHOE(HttpStatus.NOT_FOUND, "Not Found Shoe"),



    // Account
    INVALID_REQUEST_ACCOUNT_TYPE(HttpStatus.BAD_REQUEST, "Bad Request AccountType"),
}