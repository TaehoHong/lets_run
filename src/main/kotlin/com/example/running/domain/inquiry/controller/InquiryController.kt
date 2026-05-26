package com.example.running.domain.inquiry.controller

import com.example.running.domain.inquiry.controller.dto.InquiryRequest
import com.example.running.domain.inquiry.controller.dto.InquiryResponse
import com.example.running.domain.inquiry.service.InquiryService
import com.example.running.helper.authenticateWithUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/inquiries")
@RestController
class InquiryController(
    private val inquiryService: InquiryService,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: InquiryRequest): InquiryResponse {
        return authenticateWithUser { userId ->
            inquiryService.create(userId, request)
        }
    }
}
