package com.example.running.utils

import io.github.oshai.kotlinlogging.KLogging
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder


class WebClientUtils {

    companion object {

        val log = KotlinLogging.logger{}

        fun postRequest(
            url: String,
            queryParams: MultiValueMap<String, String>? = null,
            requestBody: Map<String, String>? = null
        ) = UriComponentsBuilder.fromHttpUrl(url)
            .queryParams(queryParams)
            .build()
            .toUri()
            .let {
                log.info { "uri : $it" }
                WebClient.create()
                    .post()
                    .uri(it)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()
            }

    }
}