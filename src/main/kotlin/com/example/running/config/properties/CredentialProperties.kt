package com.example.running.config.properties

import com.example.running.utils.readFileAsString
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.also

@Component
class CredentialProperties(
    private val objectMapper: ObjectMapper
) {

    @Value("\${credentials.file-path.google}")
    private val googleFilePath: String? = null

    companion object {
        lateinit var googleClientId: String
        lateinit var googleClientSecret: String
    }


    @PostConstruct
    fun init() {

        assert(googleFilePath != null)

        readFileAsString(googleFilePath!!)
            .let {
                objectMapper.readValue(it, GoogleCredential::class.java)
            }.also {
                googleClientId = it.clientId
                googleClientSecret = it.clientSecret
            }
    }
}