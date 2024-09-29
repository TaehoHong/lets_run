package com.example.running.domain.point.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.user.entity.User
import jakarta.persistence.*

@Entity
class UserPoint(

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val user: User,

    @Column(name = "point", nullable = false, columnDefinition = "INT UNSIGNED")
    var point: Int = 0

): BaseDatetime() {

    @Id @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userId: Long = user.id

    fun updatePoint(point: Int) {
        this.point += point
    }
}