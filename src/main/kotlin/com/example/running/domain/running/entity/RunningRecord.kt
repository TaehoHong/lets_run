package com.example.running.domain.running.entity

import com.example.running.common.entity.CreateDateTime
import com.example.running.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime

@Entity
class RunningRecord(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val user: User,

    @Column(name = "distance", nullable = false, columnDefinition = "INT UNSIGNED")
    val distance: Long = 0,

    @Column(name = "duration_sec", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val durationSec: Long = 0,

    @Column(name = "cadence", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val cadence: Short = 0,

    @Column(name = "heart_rate", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val heartRate: Short = 0,

    @Column(name = "calorie", nullable = false, columnDefinition = "INT UNSIGNED")
    val calorie: Int = 0,

    @ColumnDefault("0")
    @Column(name = "is_user_input", nullable = false, columnDefinition = "TINYINT(1)")
    val isUserInput: Boolean = false,

    @ColumnDefault("1")
    @Column(name = "is_statistic_included", nullable = false, columnDefinition = "TINYINT(1)")
    val isStatisticIncluded: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_end", nullable = false, columnDefinition = "TINYINT(1)")
    val isEnd: Boolean = false,

    @Column(name = "start_datetime", nullable = false, columnDefinition = "DATETIME")
    val startDatetime: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "end_datetime", columnDefinition = "DATETIME")
    val endDatetime: OffsetDateTime? = null

): CreateDateTime(){

    constructor(userId: Long): this(
        user = User(id = userId)
    )
}