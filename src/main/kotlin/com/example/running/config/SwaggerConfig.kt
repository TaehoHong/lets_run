package com.example.running.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
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

        val securityScheme = SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")
            .bearerFormat("JWT")
            .scheme("bearer")

        val securityRequirement = SecurityRequirement()
            .addList("jwt token")

        val paths = "/api/v1/**"
        return GroupedOpenApi.builder()
            .group("v1 API")
            .pathsToMatch(paths)
            .addOpenApiCustomizer { openApi ->
                openApi.addSecurityItem(securityRequirement).components
                    .addSecuritySchemes("jwt token", securityScheme)
            }
            .build()
    }
}