package com.example.running.domain.inquiry.entity

import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Entity
class Inquiry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tracking_no", nullable = false, unique = true, length = 32)
    val trackingNo: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "type", nullable = false, length = 20)
    val type: String,

    @Column(name = "title", nullable = false, length = 254)
    val title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "reply_email", nullable = false, length = 254)
    val replyEmail: String,

    @Column(name = "app_version", length = 50)
    val appVersion: String? = null,

    @Column(name = "build_number", length = 50)
    val buildNumber: String? = null,

    @Column(name = "device_model", length = 100)
    val deviceModel: String? = null,

    @Column(name = "os_name", length = 50)
    val osName: String? = null,

    @Column(name = "os_version", length = 50)
    val osVersion: String? = null,

    @Column(name = "error_code", length = 100)
    val errorCode: String? = null,

    @Column(name = "screen_name", length = 100)
    val screenName: String? = null,

    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
)
