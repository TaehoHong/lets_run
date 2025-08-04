package com.example.running.domain.auth.service

import com.example.running.config.properties.Credential
import com.example.running.utils.readResourceAsString
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SecureDigestAlgorithm
import io.jsonwebtoken.security.SignatureAlgorithm
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

data class AppleCredential(
    val keyId: String?,
    val teamId: String?,
    val clientId: String?,
    val privateKeyPath: String?
)

@Service
class AppleClientSecretService(
    private val objectMapper: ObjectMapper,
) {

    @Value("\${credentials.file-path.apple}")
    private val appleFilePath: String? = null

    private var appleCredential: AppleCredential? = null

    @PostConstruct
    fun init() {
        assert(appleFilePath != null)
        appleCredential = objectMapper.readValue(readResourceAsString(appleFilePath!!), AppleCredential::class.java)
    }

    fun getCredential() = Credential(appleCredential!!.clientId!!, generateClientSecret())

    private fun generateClientSecret(): String {
        val privateKey = loadPrivateKey()
        val now = Instant.now()

        return Jwts.builder()
            .header()
            .keyId(this.appleCredential!!.keyId)
            .add("alg", "ES256").and()
            .issuer(this.appleCredential!!.teamId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(86400 * 180)))
            .audience().add("https://appleid.apple.com").and()
            .subject(this.appleCredential!!.clientId)
            .signWith(privateKey)
            .compact()
    }

    private fun loadPrivateKey(): PrivateKey {
        val privateKeyContent = ClassPathResource(this.appleCredential!!.privateKeyPath!!).inputStream.bufferedReader()
            .readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val decoded = Base64.getDecoder().decode(privateKeyContent)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("EC")

        return keyFactory.generatePrivate(keySpec)
    }
}