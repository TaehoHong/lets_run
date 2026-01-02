package com.example.running.domain.running.entity

import com.example.running.domain.common.entity.CreatedDatetime
import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Entity
class RunningRecord(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", referencedColumnName = "id")
    var shoe: Shoe? = null,

    @Column(name = "distance", nullable = false)
    var distance: Int = 0,

    @Column(name = "duration_sec", nullable = false)
    var durationSec: Long = 0,

    @Column(name = "cadence", nullable = false)
    var cadence: Short = 0,

    @Column(name = "heart_rate", nullable = false)
    var heartRate: Short = 0,

    @Column(name = "calorie", nullable = false)
    var calorie: Int = 0,

    @ColumnDefault("0")
    @Column(name = "is_user_input", nullable = false)
    val isUserInput: Boolean = false,

    @ColumnDefault("1")
    @Column(name = "is_statistic_included", nullable = false)
    val isStatisticIncluded: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_end", nullable = false)
    var isEnd: Boolean = false,

    @Column(name = "start_datetime", nullable = false, columnDefinition = "DATETIME")
    var startDatetime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),

    @Column(name = "end_datetime", columnDefinition = "DATETIME")
    var endDatetime: OffsetDateTime? = null

): CreatedDatetime(){

    constructor(id: Long): this(
        id = id,
        user = User(id = 0)
    )

    constructor(userId: Long, shoeId: Long? = null, startDateTime: OffsetDateTime): this(
        user = User(id = userId),
        shoe = shoeId?.let{ Shoe(id = it) },
        startDatetime = startDateTime,
    )

    fun update(shoeId: Long?,
               distance: Int?,
               durationSec: Long?,
               cadence: Short?,
               heartRate: Short?,
               calorie: Int?,
               startDatetime: OffsetDateTime?,
               endDatetime: OffsetDateTime?
    ){
        this.isEnd = true
        shoeId?.also { this.shoe = Shoe(id = it)  }
        distance?.also{ this.distance = it }
        durationSec?.also{ this.durationSec = it }
        cadence?.also{ this.cadence = it }
        heartRate?.also { this.heartRate = it }
        calorie?.also { this.calorie = it }
        startDatetime?.also { this.startDatetime = it }
        endDatetime?.also { this.endDatetime = it }
    }
}