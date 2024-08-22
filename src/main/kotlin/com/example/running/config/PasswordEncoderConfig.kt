package com.example.running.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Configuration
class PasswordEncoderConfig {

    private val PASSWORD_STRENGTH = 11;

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder(PASSWORD_STRENGTH)
}