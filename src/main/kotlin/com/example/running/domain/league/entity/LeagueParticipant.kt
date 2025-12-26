package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.league.enums.BotType
import com.example.running.domain.league.enums.PromotionStatus
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

    @Column(name = "bot_type", columnDefinition = "VARCHAR(16)")
    @Enumerated(EnumType.STRING)
    val botType: BotType? = null,

    @Column(name = "bot_name", columnDefinition = "VARCHAR(32)")
    val botName: String? = null,

    @ColumnDefault("0")
    @Column(name = "is_protected", nullable = false, columnDefinition = "TINYINT(1)")
    var isProtected: Boolean = false,

    @ColumnDefault("0")
    @Column(name = "is_result_checked", nullable = false, columnDefinition = "TINYINT(1)")
    var isResultChecked: Boolean = false,

    // 봇 스케줄링 필드
    @Column(name = "scheduled_update_slot", columnDefinition = "TINYINT UNSIGNED")
    val scheduledUpdateSlot: Int? = null,

    @Column(name = "last_bot_update_date", columnDefinition = "DATE")
    var lastBotUpdateDate: LocalDate? = null

) : BaseDatetime() {

    fun updateDistance(distance: Long) {
        if (distance > this.totalDistance) {
            this.totalDistance = distance
            this.distanceAchievedAt = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    fun addDistance(distance: Long) {
        this.totalDistance += distance
        this.distanceAchievedAt = OffsetDateTime.now(ZoneOffset.UTC)
    }

    /**
     * 봇 거리 업데이트 (스케줄링용)
     */
    fun updateBotDistance(distance: Long, updateDate: LocalDate) {
        if (distance > this.totalDistance) {
            this.totalDistance = distance
            this.distanceAchievedAt = OffsetDateTime.now(ZoneOffset.UTC)
        }
        this.lastBotUpdateDate = updateDate
    }

    fun setResult(rank: Int, status: PromotionStatus) {
        this.finalRank = rank
        this.promotionStatus = status
    }

    fun markResultChecked() {
        this.isResultChecked = true
    }

    /**
     * 1차 정산 시 보호 플래그 설정 (Soft Lock)
     * - 승격/유지 상태인 경우 보호
     */
    fun markProtected() {
        if (promotionStatus in listOf(PromotionStatus.PROMOTED, PromotionStatus.MAINTAINED, PromotionStatus.REBIRTH)) {
            this.isProtected = true
        }
    }

    /**
     * 표시 이름 (봇이면 봇 이름, 유저면 유저 닉네임)
     */
    fun getDisplayName(): String {
        return if (isBot) {
            botName ?: "러너"
        } else {
            user?.nickname ?: "러너"
        }
    }

    companion object {
        fun createBot(
            group: LeagueGroup,
            distance: Long,
            botType: BotType,
            botName: String,
            scheduledUpdateSlot: Int? = null
        ): LeagueParticipant {
            return LeagueParticipant(
                group = group,
                user = null,
                totalDistance = distance,
                distanceAchievedAt = OffsetDateTime.now(ZoneOffset.UTC),
                isBot = true,
                botType = botType,
                botName = botName,
                scheduledUpdateSlot = scheduledUpdateSlot
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
