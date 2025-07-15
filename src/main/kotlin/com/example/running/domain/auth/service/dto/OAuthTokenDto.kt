package com.example.running.domain.auth.service.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OAuthTokenDto(

    @JsonProperty(value = "access_token")
    val accessToken: String,
    @JsonProperty(value = "id_token")
    val idToken: String
)
