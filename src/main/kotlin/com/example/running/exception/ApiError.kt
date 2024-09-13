package com.example.running.exception

enum class ApiError(val message: String) {

    BAD_REQUEST_FIELD_VALID_ERROR("입력값을 확인해주세요."),
    NOT_FOUND_USER_ACCOUNT("유저 계정이 존재하지 않습니다.")
}