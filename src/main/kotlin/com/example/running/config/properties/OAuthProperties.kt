package com.example.running.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding


@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties @ConstructorBinding constructor(
    val google: OAuthUrl,
    val apple: OAuthUrl
)

data class OAuthUrl(
    val tokenUrl: String,
    val redirectUrl: String? = null,
)