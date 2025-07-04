package com.example.running.domain.auth.controller.dto

import jakarta.validation.constraints.NotNull

class LoginRequest(

    @field:NotNull
    email: String,

    @field:NotNull
    password: String
)