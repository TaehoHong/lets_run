package com.example.running.domain.point.repository

import com.example.running.domain.point.entity.QPointType.Companion.pointType
import com.example.running.domain.point.entity.QUserPointHistory.Companion.userPointHistory
import com.example.running.domain.point.entity.UserPointHistory
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface UserPointHistoryRepository: JpaRepository<UserPointHistory, Long>, QUserPointHistoryRepository {

}


interface QUserPointHistoryRepository {

    fun findAll(userId: Long, cursor: Long?, isEarned: Boolean?, startCreatedDatetime: OffsetDateTime?, size: Int): List<UserPointHistory>
    fun existsBy(userId: Long, cursor: Long?, isEarned: Boolean?, startCreatedDatetime: OffsetDateTime?): Boolean
}

@Repository
class QUserPointHistoryRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
): QUserPointHistoryRepository {

    override fun findAll(userId: Long, cursor: Long?, isEarned: Boolean?, startCreatedDatetime: OffsetDateTime?, size: Int): List<UserPointHistory> {

        return queryFactory.selectFrom(userPointHistory)
            .innerJoin(userPointHistory.pointType, pointType).fetchJoin()
            .where(getBooleanBuilder(userId, cursor, isEarned, startCreatedDatetime))
            .limit(size.toLong())
            .orderBy(userPointHistory.id.desc())
            .fetch()
    }

    override fun existsBy(userId: Long, cursor: Long?, isEarned: Boolean?, startCreatedDatetime: OffsetDateTime?): Boolean {
        return queryFactory.select(Expressions.TRUE)
            .from(userPointHistory)
            .where(getBooleanBuilder(userId, cursor, isEarned, startCreatedDatetime))
            .limit(1)
            .fetchOne() ?: false
    }

    private fun getBooleanBuilder(userId: Long, cursor: Long?, isEarned: Boolean?, startCreatedDatetime: OffsetDateTime?): BooleanBuilder {
        return BooleanBuilder(userPointHistory.userId.eq(userId))
            .also {
                if(cursor != null) {
                    it.and(userPointHistory.id.lt(userId))
                }

                if(isEarned != null) {
                    if(isEarned) {
                        it.and(userPointHistory.point.goe(0))
                    } else {
                        it.and(userPointHistory.point.loe(0))
                    }
                }

                if(startCreatedDatetime != null) {
                    it.and(userPointHistory.createdDatetime.goe(startCreatedDatetime))
                }
            }

    }
}