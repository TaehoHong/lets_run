package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime

@Entity
@Table(name = "league_participant")
class LeagueParticipant(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, referencedColumnName = "id")
    val group: LeagueGroup,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    val user: User? = null,

    @ColumnDefault("0")
    @Column(name = "total_distance", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    var totalDistance: Long = 0,

    @Column(name = "distance_achieved_at", columnDefinition = "DATETIME")
    var distanceAchievedAt: OffsetDateTime? = null,

    @Column(name = "final_rank", columnDefinition = "INT UNSIGNED")
    var finalRank: Int? = null,

    @Column(name = "promotion_status", columnDefinition = "VARCHAR(16)")
    @Enumerated(EnumType.STRING)
    var promotionStatus: PromotionStatus? = null,

    @ColumnDefault("0")
    @Column(name = "is_bot", nullable = false, columnDefinition = "TINYINT(1)")
    val isBot: Boolean = false,

    @ColumnDefault("0")
    @Column(name = "is_result_checked", nullable = false, columnDefinition = "TINYINT(1)")
    var isResultChecked: Boolean = false

) : BaseDatetime() {

    fun updateDistance(distance: Long) {
        if (distance > this.totalDistance) {
            this.totalDistance = distance
            this.distanceAchievedAt = OffsetDateTime.now()
        }
    }

    fun addDistance(distance: Long) {
        this.totalDistance += distance
        this.distanceAchievedAt = OffsetDateTime.now()
    }

    fun setResult(rank: Int, status: PromotionStatus) {
        this.finalRank = rank
        this.promotionStatus = status
    }

    fun markResultChecked() {
        this.isResultChecked = true
    }

    companion object {
        fun createBot(group: LeagueGroup, distance: Long): LeagueParticipant {
            return LeagueParticipant(
                group = group,
                user = null,
                totalDistance = distance,
                distanceAchievedAt = OffsetDateTime.now(),
                isBot = true
            )
        }

        fun createParticipant(group: LeagueGroup, user: User): LeagueParticipant {
            return LeagueParticipant(
                group = group,
                user = user,
                isBot = false
            )
        }
    }
}
