package com.example.running.domain.user.service

import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.UserConfigurationRepository
import com.example.running.domain.notification.repository.NotificationRepository
import com.example.running.exception.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserConfigurationService(
    private val userConfigurationRepository: UserConfigurationRepository,
    private val notificationRepository: NotificationRepository,
) {

    @Transactional
    fun getOrCreate(userId: Long): UserConfiguration {
        return userConfigurationRepository.findById(userId)
            .orElseGet { getOrCreateForUpdate(userId) }
    }

    @Transactional
    fun getOrCreateForUpdate(userId: Long): UserConfiguration {
        userConfigurationRepository.insertIgnore(userId)
        return userConfigurationRepository.findByUserIdForUpdate(userId)
            ?: throw RuntimeException("사용자 설정을 찾을 수 없습니다.")
    }

    @Transactional
    fun update(userId: Long, healthImportEnabled: Boolean?, language: String? = null): UserConfiguration {
        return getOrCreateForUpdate(userId).apply {
            language?.let { code ->
                languageId = if (code == "system") null else notificationRepository.languageId(code)
                    ?: throw ApiException("Unsupported language", HttpStatus.BAD_REQUEST)
            }
            update(healthImportEnabled = healthImportEnabled, language = language)
        }
    }
}
