package com.example.running.domain.term.entity

import jakarta.persistence.*

enum class TermType {
    SERVICE,
    PRIVATE,
    LOCATION,
}

@Entity
class Term(
    @Id
    val id: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: TermType,

    @Column(name = "link", nullable = false)
    val link: String,

    @Column(name = "version", nullable = false)
    val version: String,

    @Column(name = "is_required", nullable = false)
    val isRequired: Boolean,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean,
)