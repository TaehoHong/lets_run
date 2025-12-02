package com.example.running.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class S3Config {

    @Value("\${cloud.aws.credentials.access-key:}")
    private lateinit var accessKey: String

    @Value("\${cloud.aws.credentials.secret-key:}")
    private lateinit var secretKey: String

    @Value("\${cloud.aws.region.static:ap-northeast-2}")
    private lateinit var region: String

    @Bean
    fun s3Client(): S3Client {
        return if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()
        } else {
            // IAM Role 또는 환경변수에서 자동으로 credentials 로드
            S3Client.builder()
                .region(Region.of(region))
                .build()
        }
    }
}
