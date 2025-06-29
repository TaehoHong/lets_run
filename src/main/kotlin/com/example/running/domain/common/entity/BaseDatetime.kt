package com.example.running.domain.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.OffsetDateTime


@MappedSuperclass
open class BaseDatetime {

    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_datetime", nullable = false, columnDefinition = "DATETIME")
    val updatedDateTime: OffsetDateTime = OffsetDateTime.now()
}