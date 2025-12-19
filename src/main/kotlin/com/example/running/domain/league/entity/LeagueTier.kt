package com.example.running.domain.league.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "league_tier")
class LeagueTier(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val id: Int,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(32)")
    val name: String,

    @Column(name = "display_order", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val displayOrder: Int
)
