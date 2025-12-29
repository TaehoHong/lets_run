package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.CreatedDatetime
import com.example.running.domain.league.enums.LeagueSessionState
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime

@Entity
@Table(name = "league_session")
class LeagueSession(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false, referencedColumnName = "id")
    val tier: LeagueTier,

    @Column(name = "start_datetime", nullable = false, columnDefinition = "DATETIME")
    val startDatetime: OffsetDateTime,

    @Column(name = "end_datetime", nullable = false, columnDefinition = "DATETIME")
    val endDatetime: OffsetDateTime,

    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1)")
    var isActive: Boolean = true,

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "state", nullable = false, columnDefinition = "VARCHAR(16)")
    var state: LeagueSessionState = LeagueSessionState.ACTIVE

) : CreatedDatetime() {

    fun deactivate() {
        this.isActive = false
        this.state = LeagueSessionState.FINALIZED
    }

    fun isEnded(now: OffsetDateTime): Boolean {
        return now.isAfter(endDatetime)
    }

    /**
     * 시즌 상태 전이
     */
    fun transitionTo(newState: LeagueSessionState) {
        this.state = newState
        if (newState == LeagueSessionState.FINALIZED) {
            this.isActive = false
        }
    }

    /**
     * 잠금 상태로 전환 (시즌 마감)
     */
    fun lock() {
        transitionTo(LeagueSessionState.LOCKED)
    }

    /**
     * 정산 시작
     */
    fun startCalculating() {
        transitionTo(LeagueSessionState.CALCULATING)
    }

    /**
     * 검수 상태로 전환 (지연 업로드 허용)
     */
    fun startAuditing() {
        transitionTo(LeagueSessionState.AUDITING)
    }

    /**
     * 최종 확정
     */
    fun finalize() {
        transitionTo(LeagueSessionState.FINALIZED)
    }

    /**
     * 새 기록 등록 가능 여부
     */
    fun canAcceptNewRecords(): Boolean = state.canAcceptNewRecords()

    /**
     * 지연 업로드 가능 여부
     */
    fun canAcceptLateRecords(): Boolean = state.canAcceptLateRecords()
}
