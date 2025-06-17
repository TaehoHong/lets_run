package com.example.running.helper

import com.example.running.security.vo.AuthenticationVo
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.context.SecurityContextHolder

fun <T> authenticateWithUser(action: (Long) -> T): T {
    val userId = getUserIdFromContextHolder()
        ?: run { throw AuthenticationServiceException("Authentication Failure") }

    return action(userId)
}

private fun getUserIdFromContextHolder(): Long? {

    val details = SecurityContextHolder.getContext().authentication?.details

    return details?.let { details ->
        (details as AuthenticationVo).id
    } ?: null
}