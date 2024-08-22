package com.example.running.user.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

class UserCreationRequest (

    @field:NotNull
    @field:Email
    val email: String,

    @field:NotNull
    @field:Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*_-]){8,20}$")
    val password: String,

    @field:NotNull
    @field:Length(min = 10)
    val nickname: String,

//    @field:NotNull
    val phoneNumber: String?
)