package com.example.running.common.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.OffsetDateTime

@EntityListeners(AuditingEntityListener::class)
open class CreateDateTime {

    @CreatedDate
    @Column(name = "create_datetime", nullable = false, columnDefinition = "DATETIME")
    val createDatetime: OffsetDateTime? = null
}