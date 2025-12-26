package com.example.running.domain.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.OffsetDateTime
import java.time.ZoneOffset

@MappedSuperclass
open class CreatedDatetime {

    @Column(name = "created_datetime", nullable = false, columnDefinition = "DATETIME")
    val createdDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}