package com.example.running.domain.achievement.entity

import com.example.running.domain.common.entity.CreatedDatetime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "title")
class Title(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = true)
    val achievement: Achievement? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description", nullable = true)
    val description: String? = null,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int

) : CreatedDatetime() {

    constructor(id: Long) : this(
        id = id,
        achievement = null,
        name = "",
        description = null,
        isEnabled = true,
        displayOrder = 0
    )
}
