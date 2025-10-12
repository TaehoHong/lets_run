package com.example.running.config

import com.example.running.common.filter.RequestResponseWrapperFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilterConfig {

    @Bean
    fun contentCachingWrapperFiler() = FilterRegistrationBean(RequestResponseWrapperFilter()).apply {
        this.order = 0
        this.addUrlPatterns("/*")
    }
}