package com.example.running.domain.league.entity

import com.example.running.domain.common.entity.CreatedDatetime
import jakarta.persistence.*

@Entity
@Table(name = "league_group")
class LeagueGroup(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false, referencedColumnName = "id")
    val season: LeagueSeason,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false, referencedColumnName = "id")
    val tier: LeagueTier

) : CreatedDatetime()
