package com.example.running.domain.common.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.OffsetDateTime


@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class BaseDatetime {

    @CreatedDate
    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now()

    @LastModifiedDate
    @Column(name = "updated_datetime", nullable = false, columnDefinition = "DATETIME")
    val updatedDateTime: OffsetDateTime = OffsetDateTime.now()
}