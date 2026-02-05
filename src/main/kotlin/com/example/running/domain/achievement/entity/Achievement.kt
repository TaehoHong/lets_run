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
@Table(name = "achievement")
class Achievement(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: AchievementCategory,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description", nullable = false)
    val description: String,

    @Column(name = "goal_value", nullable = false)
    val goalValue: Int,

    @Column(name = "reward_point", nullable = false)
    val rewardPoint: Int,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true

) : CreatedDatetime() {

    constructor(id: Long) : this(
        id = id,
        category = AchievementCategory(0),
        name = "",
        description = "",
        goalValue = 0,
        rewardPoint = 0,
        displayOrder = 0,
        isEnabled = true
    )
}
