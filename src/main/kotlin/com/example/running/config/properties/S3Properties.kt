package com.example.running.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cloud.aws.s3")
data class S3Properties(
    val bucket: String = "",
    val profileImagePath: String = "profile-images"
)
