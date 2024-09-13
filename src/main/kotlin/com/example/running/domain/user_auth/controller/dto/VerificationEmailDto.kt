package com.example.running.user.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull

class VerificationEmailDto (

    @field:NotNull
    @field:Email
    val email: String
)