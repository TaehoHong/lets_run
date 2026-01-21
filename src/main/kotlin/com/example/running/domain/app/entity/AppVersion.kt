package com.example.running.domain.app.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Entity
@Table(name = "app_version")
class AppVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    val platform: Platform,

    @Column(name = "minimum_version", nullable = false, length = 32)
    val minimumVersion: String,

    @Column(name = "message", length = 512)
    val message: String? = null,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true,

    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),

    @Column(name = "updated_datetime", nullable = false, columnDefinition = "DATETIME")
    val updatedDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),

    @Column(name = "updated_by", length = 64)
    val updatedBy: String? = null
)
