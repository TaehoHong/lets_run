package com.example.running.domain.user.service

import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.repository.UserConfigurationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserConfigurationService(
    private val userConfigurationRepository: UserConfigurationRepository
) {

    @Transactional
    fun getOrCreate(userId: Long): UserConfiguration {
        return userConfigurationRepository.findById(userId)
            .orElseGet { userConfigurationRepository.save(UserConfiguration(userId = userId)) }
    }

    @Transactional
    fun getOrCreateForUpdate(userId: Long): UserConfiguration {
        userConfigurationRepository.insertIgnore(userId)
        return userConfigurationRepository.findByUserIdForUpdate(userId)
            ?: throw RuntimeException("사용자 설정을 찾을 수 없습니다.")
    }

    @Transactional
    fun update(userId: Long, healthImportEnabled: Boolean?): UserConfiguration {
        return getOrCreate(userId).apply {
            update(healthImportEnabled = healthImportEnabled)
        }
    }
}
