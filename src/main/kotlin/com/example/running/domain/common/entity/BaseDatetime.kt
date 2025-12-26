package com.example.running.domain.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.OffsetDateTime
import java.time.ZoneOffset


@MappedSuperclass
open class BaseDatetime {

    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    @Column(name = "updated_datetime", nullable = false, columnDefinition = "DATETIME")
    val updatedDateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}