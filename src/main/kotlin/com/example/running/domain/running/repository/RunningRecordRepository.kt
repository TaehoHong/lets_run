package com.example.running.domain.running.repository

import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.entity.QRunningRecord.Companion.runningRecord
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningRecordSource
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface RunningRecordRepository : JpaRepository<RunningRecord, Long>, QRunningRecordRepository {

    fun findByIdAndUserId(id: Long, userId: Long): RunningRecord?
    fun existsByIdAndUserId(id: Long, userId: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdAndSourceAndExternalId(
        userId: Long,
        source: RunningRecordSource,
        externalId: String
    ): RunningRecord?

    fun findFirstByUserIdAndSourceAndEndDatetimeIsNotNullOrderByEndDatetimeDesc(
        userId: Long,
        source: RunningRecordSource
    ): RunningRecord?
}

interface QRunningRecordRepository {
    fun updateIsEndById(isEnd: Boolean, id: Long)
    fun getAllByUserIdAndEndDatetimeBetween(
        userId: Long,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): List<RunningRecord>

    fun findAllByCursor(userId: Long, request: RunningRecordSearchRequest): List<RunningRecord>
    fun existsByCursor(userId: Long, cursor: Long?, request: RunningRecordSearchRequest): Boolean
    fun findAllDistanceByShoeId(shoeId: Long): List<Int>
    fun findOverlapCandidates(userId: Long, start: OffsetDateTime, end: OffsetDateTime): List<RunningRecord>
    fun markPointAwardedIfEligible(userId: Long, runningRecordId: Long): Boolean
    fun sumIncludedDistanceByUserIdAndEndDatetimeBetween(
        userId: Long,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): Long

}

@Repository
class QRunningRecordRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : QRunningRecordRepository {

    override fun updateIsEndById(isEnd: Boolean, id: Long) {
        queryFactory.update(runningRecord)
            .set(runningRecord.isEnd, isEnd)
            .where(
                runningRecord.id.eq(id),
                runningRecord.isEnd.ne(false)
            )
            .execute()
    }

    override fun getAllByUserIdAndEndDatetimeBetween(
        userId: Long,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(
                runningRecord.user.id.eq(userId),
                runningRecord.isStatisticIncluded.isTrue,
                runningRecord.isEnd.isTrue,
//                runningRecord.startDatetime.between(start, end)
                runningRecord.startDatetime.between(start, end)
            ).fetch()
    }

    override fun findAllByCursor(userId: Long, request: RunningRecordSearchRequest): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(getBooleanBuilder(userId, request.cursor, request))
            .orderBy(runningRecord.id.desc())
            .limit(request.size.toLong())
            .fetch()
    }

    override fun existsByCursor(
        userId: Long,
        cursor: Long?,
        request: RunningRecordSearchRequest
    ): Boolean {
        return queryFactory.select(Expressions.TRUE)
            .from(runningRecord)
            .where(getBooleanBuilder(userId, cursor, request))
            .orderBy(runningRecord.id.desc())
            .fetchFirst() ?: false
    }

    private fun getBooleanBuilder(userId: Long, cursor: Long?, request: RunningRecordSearchRequest): BooleanBuilder {
        val booleanBuilder = BooleanBuilder()
            .and(runningRecord.user.id.eq(userId))
            .and(runningRecord.isStatisticIncluded.isTrue)
            .and(runningRecord.isEnd.isTrue)

        if (cursor != null) {
            booleanBuilder.and(runningRecord.id.lt(cursor))
        }

        if (request.getStartDateTime() != null) {
            booleanBuilder.and(runningRecord.startDatetime.goe(request.getStartDateTime()))
        }

        if (request.getEndDateTime() != null) {
            booleanBuilder.and(runningRecord.startDatetime.loe(request.getEndDateTime()))
        }

        return booleanBuilder
    }

    override fun findAllDistanceByShoeId(shoeId: Long): List<Int> {
        return queryFactory.select(runningRecord.distance)
            .from(runningRecord)
            .where(
                runningRecord.shoe.id.eq(shoeId),
                runningRecord.isEnd.isTrue,
                runningRecord.isStatisticIncluded.isTrue
            ).fetch()
    }

    override fun findOverlapCandidates(userId: Long, start: OffsetDateTime, end: OffsetDateTime): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(
                runningRecord.user.id.eq(userId),
                runningRecord.isEnd.isTrue,
                runningRecord.isStatisticIncluded.isTrue,
                runningRecord.endDatetime.isNotNull,
                runningRecord.startDatetime.before(end),
                runningRecord.endDatetime.after(start)
            ).fetch()
    }

    override fun markPointAwardedIfEligible(userId: Long, runningRecordId: Long): Boolean {
        return queryFactory.update(runningRecord)
            .set(runningRecord.pointAwarded, true)
            .where(
                runningRecord.id.eq(runningRecordId),
                runningRecord.user.id.eq(userId),
                runningRecord.pointEligible.isTrue,
                runningRecord.pointAwarded.isFalse
            )
            .execute() > 0
    }

    override fun sumIncludedDistanceByUserIdAndEndDatetimeBetween(
        userId: Long,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): Long {
        return queryFactory.select(runningRecord.distance.sum())
            .from(runningRecord)
            .where(
                runningRecord.user.id.eq(userId),
                runningRecord.isEnd.isTrue,
                runningRecord.isStatisticIncluded.isTrue,
                runningRecord.endDatetime.isNotNull,
                runningRecord.endDatetime.gt(start),
                runningRecord.endDatetime.loe(end)
            )
            .fetchOne()
            ?.toLong()
            ?: 0L
    }
}
