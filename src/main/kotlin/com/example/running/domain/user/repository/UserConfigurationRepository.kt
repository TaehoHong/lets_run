package com.example.running.domain.user.repository

import com.example.running.domain.user.entity.UserConfiguration
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserConfigurationRepository : JpaRepository<UserConfiguration, Long> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO user_configuration (user_id) VALUES (:userId)", nativeQuery = true)
    fun insertIgnore(@Param("userId") userId: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select configuration from UserConfiguration configuration where configuration.userId = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserConfiguration?
}
