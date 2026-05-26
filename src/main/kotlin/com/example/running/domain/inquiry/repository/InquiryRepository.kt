package com.example.running.domain.inquiry.repository

import com.example.running.domain.inquiry.entity.Inquiry
import org.springframework.data.jpa.repository.JpaRepository

interface InquiryRepository : JpaRepository<Inquiry, Long> {
    fun findFirstByTrackingNoStartingWithOrderByTrackingNoDesc(prefix: String): Inquiry?
}
