package com.example.running.common.filter

import com.example.running.common.MultipleReadableRequestWrapper
import com.example.running.common.RequestBodyTooLargeException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Instant

@Component
class RequestResponseWrapperFilter(
    private val completedRecordMaxBytes: Int = 32 * 1024 * 1024,
): OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {

        if (request.requestURI == "/api/v1/running" || request.requestURI.startsWith("/api/v1/running/")) {
            request.setAttribute("running.receivedAt", Instant.now())
        }

        val completedRecord = request.method == "POST" && request.requestURI == "/api/v1/running/completed-records"
        if (completedRecord && request.contentLengthLong > completedRecordMaxBytes) {
            rejectOversized(response)
            return
        }
        val wrappedRequest = try {
            if (completedRecord) MultipleReadableRequestWrapper(request, completedRecordMaxBytes)
            else if (request.method == "POST" && request.requestURI == "/api/v1/garmin/webhook") request
            else MultipleReadableRequestWrapper(request)
        } catch (_: RequestBodyTooLargeException) {
            rejectOversized(response)
            return
        }
        val wrappedResponse = ContentCachingResponseWrapper(response)
        filterChain.doFilter(wrappedRequest, wrappedResponse)

        wrappedResponse.copyBodyToResponse()
    }

    private fun rejectOversized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
        response.contentType = "application/json"
        response.writer.write("{\"message\":\"Completed running record exceeds request size limit\"}")
    }
}
