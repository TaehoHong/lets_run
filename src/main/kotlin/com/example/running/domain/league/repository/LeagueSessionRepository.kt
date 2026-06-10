package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.QLeagueSession.Companion.leagueSession
import com.example.running.domain.league.enums.LeagueSessionState
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface LeagueSessionRepository : JpaRepository<LeagueSession, Long>, QLeagueSessionRepository {

    fun findByIsActiveTrue(): LeagueSession?

    fun findByTierIdAndState(tierId: Int, state: LeagueSessionState): LeagueSession?
}


interface QLeagueSessionRepository {
    fun findAllIdByStatus(state: LeagueSessionState, cursor: Long?, size: Long): List<Long>
    fun hasNext(state: LeagueSessionState, cursor: Long?): Boolean
    fun findActiveByTierIdContaining(tierId: Int, now: OffsetDateTime): LeagueSession?
}


@Repository
class QLeagueSessionRepositoryImpl(
    private val queryFactory: JPAQueryFactory
): QLeagueSessionRepository {

    override fun findAllIdByStatus(state: LeagueSessionState, cursor: Long?, size: Long): List<Long> {
        return queryFactory.select(leagueSession.id)
            .from(leagueSession)
            .where(getWhereClause(state, cursor))
            .limit(size)
            .fetch()

    }

    override fun hasNext(state: LeagueSessionState, cursor: Long?): Boolean {

        if(cursor == null) return false

        return queryFactory.select(leagueSession.id)
            .from(leagueSession)
            .where(getWhereClause(state, cursor))
            .limit(1)
            .fetchOne() != null
    }

    override fun findActiveByTierIdContaining(tierId: Int, now: OffsetDateTime): LeagueSession? {
        return queryFactory.selectFrom(leagueSession)
            .where(
                leagueSession.tier.id.eq(tierId),
                leagueSession.state.eq(LeagueSessionState.ACTIVE),
                leagueSession.startDatetime.loe(now),
                leagueSession.endDatetime.goe(now)
            )
            .orderBy(
                leagueSession.startDatetime.desc(),
                leagueSession.id.desc()
            )
            .fetchFirst()
    }

    private fun getWhereClause(status: LeagueSessionState, cursor: Long?): BooleanBuilder {
        return BooleanBuilder(leagueSession.state.eq(status)).apply {
            cursor?.also {
                this.and(leagueSession.id.gt(it))
            }
        }
    }
}
