package com.example.running.domain.running.repository

import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.entity.QRunningRecord.Companion.runningRecord
import com.example.running.domain.running.entity.RunningRecord
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface RunningRecordRepository: JpaRepository<RunningRecord, Long>, QRunningRecordRepository {

    fun findByIdAndUserId(id: Long, userId: Long): RunningRecord?
}

interface QRunningRecordRepository {
    fun updateIsEndById(isEnd: Boolean, id: Long)
    fun getAllByUserIdAndEndDatetimeBetween(userId: Long, start: OffsetDateTime, end: OffsetDateTime): List<RunningRecord>
    fun findAllByCursor(userId: Long, request: RunningRecordSearchRequest): List<RunningRecord>
    fun existsByCursor(userId: Long, request: RunningRecordSearchRequest): Boolean

}

@Repository
class QRunningRecordRepositoryImpl(
    private val queryFactory: JPAQueryFactory
): QRunningRecordRepository {

    override fun updateIsEndById(isEnd: Boolean, id: Long) {
        queryFactory.update(runningRecord)
            .set(runningRecord.isEnd, isEnd)
            .where(
                runningRecord.id.eq(id),
                runningRecord.isEnd.ne(false)
            )
            .execute()
    }

    override fun getAllByUserIdAndEndDatetimeBetween(userId: Long, start: OffsetDateTime, end: OffsetDateTime): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(
                runningRecord.user.id.eq(userId),
                runningRecord.isStatisticIncluded.isTrue,
                runningRecord.isEnd.isTrue,
                runningRecord.endDatetime.between(start, end)
            ).fetch()
    }

    override fun findAllByCursor(userId: Long, request: RunningRecordSearchRequest): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(getBooleanBuilder(userId, request))
            .orderBy(runningRecord.id.desc())
            .limit(request.size.toLong())
            .fetch()
    }

    override fun existsByCursor(
        userId: Long,
        request: RunningRecordSearchRequest
    ): Boolean {
        return queryFactory.select(Expressions.TRUE)
            .from(runningRecord)
            .where(getBooleanBuilder(userId, request))
            .orderBy(runningRecord.id.desc())
            .fetchFirst()?:false
    }

    private fun getBooleanBuilder(userId: Long, request: RunningRecordSearchRequest): BooleanBuilder {
        val booleanBuilder = BooleanBuilder()
            .andAnyOf(
            runningRecord.user.id.eq(userId),
                runningRecord.isStatisticIncluded.isTrue,
        )

        if(request.cursor != null) {
            booleanBuilder.and(runningRecord.id.lt(request.cursor))
        }

        if(request.getStartDateTime() != null) {
            booleanBuilder.and(runningRecord.startDatetime.goe(request.getStartDateTime()))
        }

        if(request.getEndDateTime() != null) {
            booleanBuilder.and(runningRecord.startDatetime.loe(request.getEndDateTime()))
        }

        return booleanBuilder
    }
}