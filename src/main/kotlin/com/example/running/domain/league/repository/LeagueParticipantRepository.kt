package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.QLeagueGroup
import com.example.running.domain.league.entity.QLeagueParticipant
import com.example.running.domain.league.entity.QLeagueSeason
import com.example.running.domain.user.entity.QUser
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface LeagueParticipantRepository : JpaRepository<LeagueParticipant, Long>, QLeagueParticipantRepository {

    fun findByGroupIdOrderByTotalDistanceDescDistanceAchievedAtAsc(groupId: Long): List<LeagueParticipant>

    @Query("SELECT p FROM LeagueParticipant p WHERE p.group.id = :groupId AND p.isBot = true")
    fun findBotsByGroupId(@Param("groupId") groupId: Long): List<LeagueParticipant>

    @Query("SELECT COUNT(p) FROM LeagueParticipant p WHERE p.group.id = :groupId")
    fun countByGroupId(@Param("groupId") groupId: Long): Int

    @Query("SELECT COUNT(p) FROM LeagueParticipant p WHERE p.group.id = :groupId AND p.isBot = false")
    fun countRealParticipantsByGroupId(@Param("groupId") groupId: Long): Int

    @Query("SELECT AVG(p.totalDistance) FROM LeagueParticipant p WHERE p.group.season.id = :seasonId AND p.group.tier.id = :tierId AND p.isBot = false")
    fun findAverageDistanceBySeasonAndTier(@Param("seasonId") seasonId: Long, @Param("tierId") tierId: Int): Double?

    @Query("SELECT p FROM LeagueParticipant p WHERE p.group.season.id = :seasonId AND p.isBot = false AND p.promotionStatus IS NOT NULL")
    fun findParticipantsWithResultBySeasonId(@Param("seasonId") seasonId: Long): List<LeagueParticipant>
}

interface QLeagueParticipantRepository {
    fun findCurrentParticipantByUserId(userId: Long): LeagueParticipant?
    fun findParticipantsByGroupIdWithRanking(groupId: Long, cursor: Long?, size: Int): List<LeagueParticipant>
    fun findUncheckedResultByUserId(userId: Long): LeagueParticipant?
    fun findHistoryByUserId(userId: Long, cursor: Long?, size: Int): List<LeagueParticipant>
    fun findBotsToUpdateBySlot(seasonId: Long, slot: Int, today: LocalDate): List<LeagueParticipant>
}

class QLeagueParticipantRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : QLeagueParticipantRepository {

    private val participant = QLeagueParticipant.leagueParticipant
    private val group = QLeagueGroup.leagueGroup
    private val season = QLeagueSeason.leagueSeason
    private val user = QUser.user

    override fun findCurrentParticipantByUserId(userId: Long): LeagueParticipant? {
        return queryFactory
            .selectFrom(participant)
            .join(participant.group, group).fetchJoin()
            .join(group.season, season).fetchJoin()
            .where(
                participant.user.id.eq(userId),
                season.isActive.isTrue
            )
            .fetchOne()
    }

    override fun findParticipantsByGroupIdWithRanking(groupId: Long, cursor: Long?, size: Int): List<LeagueParticipant> {
        val query = queryFactory
            .selectFrom(participant)
            .leftJoin(participant.user, user).fetchJoin()
            .where(participant.group.id.eq(groupId))
            .orderBy(
                participant.totalDistance.desc(),
                participant.distanceAchievedAt.asc()
            )
            .limit(size.toLong())

        cursor?.let {
            query.where(participant.id.gt(it))
        }

        return query.fetch()
    }

    /**
     * 미확인 결과가 있는 참가자 조회
     * - 시즌이 종료됨 (isActive = false)
     * - 결과가 설정됨 (promotionStatus != null)
     * - 아직 확인하지 않음 (isResultChecked = false)
     */
    override fun findUncheckedResultByUserId(userId: Long): LeagueParticipant? {
        return queryFactory
            .selectFrom(participant)
            .join(participant.group, group).fetchJoin()
            .join(group.season, season).fetchJoin()
            .join(group.tier).fetchJoin()
            .where(
                participant.user.id.eq(userId),
                participant.isBot.isFalse,
                participant.promotionStatus.isNotNull,
                participant.isResultChecked.isFalse,
                season.isActive.isFalse
            )
            .orderBy(season.seasonNumber.desc())
            .fetchFirst()
    }

    /**
     * 유저의 리그 히스토리 조회 (종료된 시즌만)
     * - 시즌 번호 내림차순 정렬
     * - 커서 기반 페이지네이션
     */
    override fun findHistoryByUserId(userId: Long, cursor: Long?, size: Int): List<LeagueParticipant> {
        val query = queryFactory
            .selectFrom(participant)
            .join(participant.group, group).fetchJoin()
            .join(group.season, season).fetchJoin()
            .join(group.tier).fetchJoin()
            .where(
                participant.user.id.eq(userId),
                participant.isBot.isFalse,
                season.isActive.isFalse
            )
            .orderBy(season.seasonNumber.desc())
            .limit((size + 1).toLong()) // hasMore 확인을 위해 +1

        cursor?.let {
            query.where(participant.id.lt(it))
        }

        return query.fetch()
    }

    /**
     * 특정 슬롯의 업데이트 대상 봇 조회
     * - 해당 시즌의 봇
     * - 해당 슬롯에 배정된 봇
     * - 오늘 아직 업데이트되지 않은 봇
     */
    override fun findBotsToUpdateBySlot(seasonId: Long, slot: Int, today: LocalDate): List<LeagueParticipant> {
        return queryFactory
            .selectFrom(participant)
            .join(participant.group, group).fetchJoin()
            .where(
                group.season.id.eq(seasonId),
                participant.isBot.isTrue,
                participant.scheduledUpdateSlot.eq(slot),
                participant.lastBotUpdateDate.isNull
                    .or(participant.lastBotUpdateDate.ne(today))
            )
            .fetch()
    }
}
