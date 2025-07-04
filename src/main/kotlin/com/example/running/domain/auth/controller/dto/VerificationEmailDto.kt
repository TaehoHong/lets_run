package com.example.running.domain.auth.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull

class VerificationEmailDto (

    @field:NotNull
    @field:Email
    val email: String
)