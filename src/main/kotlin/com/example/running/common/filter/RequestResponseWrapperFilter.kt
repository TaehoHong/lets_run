package com.example.running.common.filter

import com.example.running.common.MultipleReadableRequestWrapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class RequestResponseWrapperFilter: OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {

        val wrappedResponse = ContentCachingResponseWrapper(response)

        filterChain.doFilter(MultipleReadableRequestWrapper(request), wrappedResponse)

        wrappedResponse.copyBodyToResponse()
    }
}