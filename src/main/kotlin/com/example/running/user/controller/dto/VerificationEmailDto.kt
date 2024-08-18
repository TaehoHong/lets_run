package com.example.running.user.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull

class VerificationEmailDto (

    @NotNull
    @Email
    val email: String
)