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
@Table(name = "user_title")
class UserTitle(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    val title: Title,

    @Column(name = "is_main", nullable = false)
    var isMain: Boolean = false,

    @Column(name = "acquired_datetime", nullable = false)
    val acquiredDatetime: OffsetDateTime

) : CreatedDatetime() {

    constructor(userId: Long, titleId: Long, acquiredDatetime: OffsetDateTime) : this(
        user = User(userId),
        title = Title(titleId),
        isMain = false,
        acquiredDatetime = acquiredDatetime
    )

    fun setAsMain() {
        this.isMain = true
    }

    fun unsetMain() {
        this.isMain = false
    }
}
