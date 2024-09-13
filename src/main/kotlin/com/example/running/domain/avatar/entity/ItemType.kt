package com.example.running.domain.avatar.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class ItemType(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val id: Short,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(16)")
    val name: String
)