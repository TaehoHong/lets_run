package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.RebirthMedal
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.LocalDate

@Entity
@Table(name = "user_league_info")
class UserLeagueInfo(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_tier_id", nullable = false, referencedColumnName = "id")
    var currentTier: LeagueTier,

    @ColumnDefault("0")
    @Column(name = "rebirth_count", nullable = false, columnDefinition = "INT UNSIGNED")
    var rebirthCount: Int = 0,

    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1)")
    var isActive: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "streak_count", nullable = false, columnDefinition = "INT UNSIGNED")
    var streakCount: Int = 0,

    @Column(name = "last_run_date", columnDefinition = "DATE")
    var lastRunDate: LocalDate? = null

) : BaseDatetime() {

    fun promote() {
        val nextTier = LeagueTierType.getNextTier(LeagueTierType.fromId(currentTier.id))
        if (nextTier != null) {
            this.currentTier = LeagueTier(id = nextTier.id, name = nextTier.name, displayOrder = nextTier.displayOrder)
        }
    }

    fun relegate() {
        val prevTier = LeagueTierType.getPreviousTier(LeagueTierType.fromId(currentTier.id))
        if (prevTier != null) {
            this.currentTier = LeagueTier(id = prevTier.id, name = prevTier.name, displayOrder = prevTier.displayOrder)
        }
    }

    fun rebirth() {
        this.rebirthCount++
        // 환생 시 골드 리그에서 재시작
        this.currentTier = LeagueTier(
            id = LeagueTierType.GOLD.id,
            name = LeagueTierType.GOLD.name,
            displayOrder = LeagueTierType.GOLD.displayOrder
        )
    }

    fun deactivate() {
        this.isActive = false
    }

    fun activate() {
        this.isActive = true
    }

    fun getRebirthMedal(): RebirthMedal {
        return RebirthMedal.fromRebirthCount(rebirthCount)
    }

    fun updateTier(tierId: Int) {
        val tierType = LeagueTierType.fromId(tierId)
        this.currentTier = LeagueTier(id = tierType.id, name = tierType.name, displayOrder = tierType.displayOrder)
    }

    /**
     * 연속 러닝 업데이트
     * @return 연속 러닝 일수 (보너스 계산용)
     */
    fun updateStreak(runDate: LocalDate): Int {
        val previousDate = lastRunDate

        when {
            // 첫 러닝 또는 같은 날 러닝
            previousDate == null || previousDate == runDate -> {
                if (previousDate == null) {
                    streakCount = 1
                    lastRunDate = runDate
                }
                // 같은 날이면 streak 유지
            }
            // 연속 러닝 (어제 뛴 경우)
            previousDate.plusDays(1) == runDate -> {
                streakCount++
                lastRunDate = runDate
            }
            // 연속 끊김 (하루 이상 건너뜀)
            else -> {
                streakCount = 1
                lastRunDate = runDate
            }
        }

        return streakCount
    }

    /**
     * 연속 러닝 초기화
     */
    fun resetStreak() {
        streakCount = 0
        lastRunDate = null
    }

    companion object {
        fun createNewUser(user: User): UserLeagueInfo {
            return UserLeagueInfo(
                user = user,
                currentTier = LeagueTier(
                    id = LeagueTierType.BRONZE.id,
                    name = LeagueTierType.BRONZE.name,
                    displayOrder = LeagueTierType.BRONZE.displayOrder
                )
            )
        }
    }
}
