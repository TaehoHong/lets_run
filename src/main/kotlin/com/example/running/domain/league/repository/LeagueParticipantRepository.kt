package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueParticipant
import com.example.running.domain.league.entity.QLeagueParticipant.Companion.leagueParticipant
import com.example.running.domain.league.entity.QLeagueSession.Companion.leagueSession
import com.example.running.domain.user.entity.QUser.Companion.user
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface LeagueParticipantRepository : JpaRepository<LeagueParticipant, Long>, QLeagueParticipantRepository {

    fun findByLeagueSessionIdOrderByTotalDistanceDescDistanceAchievedAtAsc(sessionId: Long): List<LeagueParticipant>

    @Query("SELECT p FROM LeagueParticipant p WHERE p.leagueSession.id = :sessionId AND p.isBot = true")
    fun findBotsByGroupId(@Param("sessionId") sessionId: Long): List<LeagueParticipant>

    @Query("SELECT COUNT(p) FROM LeagueParticipant p WHERE p.leagueSession.id = :sessionId")
    fun countBySessionId(@Param("sessionId") sessionId: Long): Int
}

interface QLeagueParticipantRepository {
    fun findCurrentParticipantByUserId(userId: Long): LeagueParticipant?
    fun findUncheckedResultByUserId(userId: Long): LeagueParticipant?
    fun findHistoryByUserId(userId: Long, cursor: Long?, size: Int): List<LeagueParticipant>
    fun findBotsToUpdateBySlot(seasonId: Long, slot: Int, today: LocalDate): List<LeagueParticipant>
    fun findAllBySessionIdWithUser(sessionId: Long): List<LeagueParticipant>
}

class QLeagueParticipantRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : QLeagueParticipantRepository {

    override fun findCurrentParticipantByUserId(userId: Long): LeagueParticipant? {
        return queryFactory
            .selectFrom(leagueParticipant)
            .join(leagueParticipant.leagueSession, leagueSession).fetchJoin()
            .where(
                leagueParticipant.user.id.eq(userId),
                leagueSession.isActive.isTrue
            )
            .fetchOne()
    }

    /**
     * 미확인 결과가 있는 참가자 조회
     * - 시즌이 종료됨 (isActive = false)
     * - 결과가 설정됨 (promotionStatus != null)
     * - 아직 확인하지 않음 (isResultChecked = false)
     */
    override fun findUncheckedResultByUserId(userId: Long): LeagueParticipant? {
        return queryFactory
            .selectFrom(leagueParticipant)
            .join(leagueParticipant.leagueSession, leagueSession).fetchJoin()
            .join(leagueSession.tier).fetchJoin()
            .where(
                leagueParticipant.user.id.eq(userId),
                leagueParticipant.isBot.isFalse,
                leagueParticipant.promotionStatus.isNotNull,
                leagueParticipant.isResultChecked.isFalse,
                leagueSession.isActive.isFalse
            )
            .orderBy(leagueSession.id.desc())
            .fetchFirst()
    }

    /**
     * 유저의 리그 히스토리 조회 (종료된 시즌만)
     * - 시즌 번호 내림차순 정렬
     * - 커서 기반 페이지네이션
     */
    override fun findHistoryByUserId(userId: Long, cursor: Long?, size: Int): List<LeagueParticipant> {
        val query = queryFactory
            .selectFrom(leagueParticipant)
            .join(leagueParticipant.leagueSession, leagueSession).fetchJoin()
            .join(leagueSession.tier).fetchJoin()
            .where(
                leagueParticipant.user.id.eq(userId),
                leagueParticipant.isBot.isFalse,
                leagueSession.isActive.isFalse
            )
            .orderBy(leagueSession.id.desc())
            .limit((size + 1).toLong()) // hasMore 확인을 위해 +1

        cursor?.let {
            query.where(leagueParticipant.id.lt(it))
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
            .selectFrom(leagueParticipant)
            .where(
                leagueParticipant.leagueSession.id.eq(seasonId),
                leagueParticipant.isBot.isTrue,
                leagueParticipant.scheduledUpdateSlot.eq(slot),
                leagueParticipant.lastBotUpdateDate.isNull
                    .or(leagueParticipant.lastBotUpdateDate.ne(today))
            )
            .fetch()
    }

    override fun findAllBySessionIdWithUser(sessionId: Long): List<LeagueParticipant> {
        return queryFactory.selectFrom(leagueParticipant)
            .leftJoin(leagueParticipant.user, user).fetchJoin()
            .where(leagueParticipant.leagueSession.id.eq(sessionId))
            .fetch()
    }
}
