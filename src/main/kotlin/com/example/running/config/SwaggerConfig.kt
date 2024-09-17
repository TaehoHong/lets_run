package com.example.running.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
    info = Info(title = "달려라 태호군 API 명세서",
        description = "API 명세서",
        version = "1.0.0")
)
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): GroupedOpenApi {

        val paths = "/api/v1/**"
        return GroupedOpenApi.builder()
            .group("v1 API")
            .pathsToMatch(paths)
            .build()
    }
}