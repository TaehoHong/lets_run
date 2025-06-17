package com.example.running.domain.user.controller

import com.example.running.domain.user.service.UserAccountService
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/users/{userId}/accounts")
@RestController
class UserAccountController(
    private val userAccountService: UserAccountService
) {

//    @PatchMapping("/{id}")
//    fun changeAccount(@PathVariable userId: Long, @PathVariable id: Long) {
//        authenticateWithUser { userId -> changeAccount(userId, id) }
//            userAccountService
//        }
//    }
}