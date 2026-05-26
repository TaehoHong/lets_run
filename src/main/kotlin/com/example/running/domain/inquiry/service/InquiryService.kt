package com.example.running.domain.inquiry.service

import com.example.running.domain.inquiry.controller.dto.InquiryRequest
import com.example.running.domain.inquiry.controller.dto.InquiryResponse
import com.example.running.domain.inquiry.entity.Inquiry
import com.example.running.domain.inquiry.repository.InquiryRepository
import com.example.running.domain.user.entity.User
import com.example.running.exception.ApiException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class InquiryService(
    private val inquiryRepository: InquiryRepository,
) {

    fun create(userId: Long, request: InquiryRequest): InquiryResponse {
        validate(request)

        repeat(2) { attempt ->
            val trackingNo = nextNo()
            try {
                inquiryRepository.saveAndFlush(request.toEntity(userId, trackingNo))
                return InquiryResponse(trackingNo)
            } catch (ex: DataIntegrityViolationException) {
                if (attempt == 1) {
                    throw ex
                }
            }
        }

        throw IllegalStateException("tracking number creation failed")
    }

    private fun validate(request: InquiryRequest) {
        if (request.type != "GENERAL" && request.type != "ERROR") {
            throw ApiException("문의 유형을 확인해주세요.", HttpStatus.BAD_REQUEST)
        }
    }

    private fun nextNo(): String {
        val prefix = "RT-${LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)}-"
        val last = inquiryRepository.findFirstByTrackingNoStartingWithOrderByTrackingNoDesc(prefix)
            ?.trackingNo
            ?.substringAfterLast("-")
            ?.toIntOrNull() ?: 0

        return "$prefix${(last + 1).toString().padStart(4, '0')}"
    }

    private fun InquiryRequest.toEntity(userId: Long, trackingNo: String): Inquiry {
        val isError = type == "ERROR"

        return Inquiry(
            trackingNo = trackingNo,
            user = User(userId),
            type = type,
            title = title,
            content = content,
            replyEmail = replyEmail,
            appVersion = appVersion.takeIf { isError },
            buildNumber = buildNumber.takeIf { isError },
            deviceModel = deviceModel.takeIf { isError },
            osName = osName.takeIf { isError },
            osVersion = osVersion.takeIf { isError },
            errorCode = errorCode.takeIf { isError },
            screenName = screenName.takeIf { isError },
        )
    }
}
