package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.league.enums.LeagueTierType
import com.example.running.domain.league.enums.RebirthMedal
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_active_season_id", referencedColumnName = "id")
    var lastActiveSeason: LeagueSeason? = null,

    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1)")
    var isActive: Boolean = true

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

    fun updateLastActiveSeason(season: LeagueSeason) {
        this.lastActiveSeason = season
    }

    fun getRebirthMedal(): RebirthMedal {
        return RebirthMedal.fromRebirthCount(rebirthCount)
    }

    fun updateTier(tierId: Int) {
        val tierType = LeagueTierType.fromId(tierId)
        this.currentTier = LeagueTier(id = tierType.id, name = tierType.name, displayOrder = tierType.displayOrder)
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
