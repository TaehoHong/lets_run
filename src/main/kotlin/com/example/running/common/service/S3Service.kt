package com.example.running.common.service

import com.example.running.config.properties.S3Properties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.*

@Service
@EnableConfigurationProperties(S3Properties::class)
class S3Service(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties
) {
    private val log = KotlinLogging.logger {}

    /**
     * 프로필 이미지 업로드
     * @param userId 사용자 ID
     * @param file 업로드할 이미지 파일
     * @return 업로드된 이미지의 S3 URL
     */
    fun uploadProfileImage(userId: Long, file: MultipartFile): String {
        val extension = file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val fileName = "${s3Properties.profileImagePath}/$userId/${UUID.randomUUID()}.$extension"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucket)
            .key(fileName)
            .contentType(file.contentType ?: "image/jpeg")
            .build()

        s3Client.putObject(
            putObjectRequest,
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        val imageUrl = "/$fileName"
        log.info { "Profile image uploaded: $imageUrl for userId: $userId" }

        return imageUrl
    }

    /**
     * 프로필 이미지 삭제
     * @param imageUrl 삭제할 이미지 URL
     */
    fun deleteProfileImage(imageUrl: String) {
        try {
            val key = imageUrl.substringAfter("${s3Properties.bucket}.s3.amazonaws.com/")

            val deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .build()

            s3Client.deleteObject(deleteObjectRequest)
            log.info { "Profile image deleted: $key" }
        } catch (e: Exception) {
            log.warn { "Failed to delete profile image: $imageUrl, error: ${e.message}" }
        }
    }
}
