package com.example.running.domain.point.repository

import com.example.running.domain.point.entity.QPointType.pointType
import com.example.running.domain.point.entity.QUserPointHistory.userPointHistory
import com.example.running.domain.point.entity.UserPointHistory
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class QUserPointHistoryRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
): QUserPointHistoryRepository {

    override fun findAll(userId: Long, id: Long?, size: Int): List<UserPointHistory> {

        val booleanBuilder = BooleanBuilder(userPointHistory.userId.eq(userId))

        if(id != null) {
            booleanBuilder.and(userPointHistory.id.lt(id))
        }

        return queryFactory.selectFrom(userPointHistory)
            .innerJoin(userPointHistory.pointType, pointType).fetchJoin()
            .where(booleanBuilder)
            .limit(size.toLong())
            .fetch()
    }
}