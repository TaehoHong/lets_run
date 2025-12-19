package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.CreatedDatetime
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime

@Entity
@Table(name = "league_season")
class LeagueSeason(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @Column(name = "season_number", nullable = false, columnDefinition = "INT UNSIGNED")
    val seasonNumber: Int,

    @Column(name = "start_datetime", nullable = false, columnDefinition = "DATETIME")
    val startDatetime: OffsetDateTime,

    @Column(name = "end_datetime", nullable = false, columnDefinition = "DATETIME")
    val endDatetime: OffsetDateTime,

    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1)")
    var isActive: Boolean = true

) : CreatedDatetime() {

    fun deactivate() {
        this.isActive = false
    }

    fun isEnded(now: OffsetDateTime): Boolean {
        return now.isAfter(endDatetime)
    }
}
