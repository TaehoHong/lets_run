package com.example.running.domain.common.enums

import com.example.running.exception.ApiError
import com.example.running.exception.ApiException

enum class AccountTypeName(val id: Short) {
    GOOGLE(2),
    APPLE(3);


    companion object {
        fun getByNameIgnoreCase(name: String): AccountTypeName {
            return entries
                .firstOrNull { it.name.equals(name, true) }
                ?: run { throw ApiException(ApiError.INVALID_REQUEST_ACCOUNT_TYPE) }
        }
    }
}