package com.example.running.domain.achievement.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "achievement_category")
class AchievementCategory(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val id: Short,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(32)")
    val name: String,

    @Column(name = "description", nullable = true)
    val description: String? = null,

    @Column(name = "icon_path", nullable = true)
    val iconPath: String? = null,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int
) {
    constructor(id: Short) : this(
        id = id,
        name = "",
        description = null,
        iconPath = null,
        displayOrder = 0
    )
}
