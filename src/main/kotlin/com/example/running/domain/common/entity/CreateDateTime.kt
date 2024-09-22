package com.example.running.common.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.OffsetDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class CreateDateTime {

    @CreatedDate
    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now()
}