package com.example.running.domain.user_auth.service.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GoogleTokenDto(

    @JsonProperty(value = "access_token")
    val accessToken: String,
    @JsonProperty(value = "id_token")
    val idToken: String
)
