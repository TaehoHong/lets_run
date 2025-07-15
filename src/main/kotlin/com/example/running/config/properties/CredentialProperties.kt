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
    @Value("\${credentials.file-path.apple}")
    private val appleFilePath: String? = null

    companion object {
        lateinit var googleCredential: Credential
        lateinit var appleCredential: Credential
    }


    @PostConstruct
    fun init() {

        assert(googleFilePath != null)

        googleCredential = readFileAsString(googleFilePath!!)
            .let { objectMapper.readValue(it, Credential::class.java) }

        assert(appleFilePath != null)

        appleCredential = readFileAsString(appleFilePath!!)
            .let { objectMapper.readValue(it, Credential::class.java) }
    }
}