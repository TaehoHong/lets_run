package com.example.running.domain.inquiry.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class InquiryRequest(
    @field:NotBlank
    val type: String,

    @field:NotBlank
    val title: String,

    @field:NotBlank
    val content: String,

    @field:NotBlank
    @field:Email
    val replyEmail: String,

    val appVersion: String? = null,
    val buildNumber: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val errorCode: String? = null,
    val screenName: String? = null,
)
