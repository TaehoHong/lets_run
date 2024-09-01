package com.example.running.user.controller.dto

import jakarta.validation.constraints.NotNull

class LoginRequest(

    @field:NotNull
    email: String,

    @field:NotNull
    password: String
)