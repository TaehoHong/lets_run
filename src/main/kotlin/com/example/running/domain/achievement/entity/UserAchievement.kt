package com.example.running.domain.achievement.entity

import com.example.running.domain.common.entity.CreatedDatetime
import com.example.running.domain.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "user_achievement")
class UserAchievement(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    val achievement: Achievement,

    @Column(name = "achieved_datetime", nullable = false)
    val achievedDatetime: OffsetDateTime

) : CreatedDatetime() {

    constructor(userId: Long, achievementId: Long, achievedDatetime: OffsetDateTime) : this(
        user = User(userId),
        achievement = Achievement(achievementId),
        achievedDatetime = achievedDatetime
    )
}
