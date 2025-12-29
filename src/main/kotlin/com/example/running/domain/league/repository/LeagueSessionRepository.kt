package com.example.running.domain.league.repository

import com.example.running.domain.league.entity.LeagueSession
import com.example.running.domain.league.entity.QLeagueSession.Companion.leagueSession
import com.example.running.domain.league.enums.LeagueSessionState
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LeagueSessionRepository : JpaRepository<LeagueSession, Long>, QLeagueSessionRepository {

    fun findByIsActiveTrue(): LeagueSession?
}


interface QLeagueSessionRepository {
    fun findAllIdByStatus(state: LeagueSessionState, cursor: Long?, size: Long): List<Long>
    fun hasNext(state: LeagueSessionState, cursor: Long?): Boolean
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

    private fun getWhereClause(status: LeagueSessionState, cursor: Long?): BooleanBuilder {
        return BooleanBuilder(leagueSession.state.eq(status)).apply {
            cursor?.also {
                this.and(leagueSession.id.gt(it))
            }
        }
    }
}