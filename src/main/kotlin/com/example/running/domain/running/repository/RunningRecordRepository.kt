package com.example.running.domain.running.repository

import com.example.running.domain.running.entity.QRunningRecord.runningRecord
import com.example.running.domain.running.entity.RunningRecord
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface RunningRecordRepository: JpaRepository<RunningRecord, Long> {

    fun findByUserId(userId: Long, pageable: Pageable): Page<RunningRecord>
    fun findByIdAndUserId(id: Long, userId: Long): RunningRecord?
}

@Repository
class RunningRecordQueryRepository(
    private val queryFactory: JPAQueryFactory
) {

    fun updateIsEndById(isEnd: Boolean, id: Long) {
        queryFactory.update(runningRecord)
            .set(runningRecord.isEnd, isEnd)
            .where(
                runningRecord.id.eq(id),
                runningRecord.isEnd.ne(false)
            )
            .execute()
    }

    fun getAllByUserIdAndEndDatetimeBetween(userId: Long, start: OffsetDateTime, end: OffsetDateTime): List<RunningRecord> {
        return queryFactory.selectFrom(runningRecord)
            .where(
                runningRecord.user.id.eq(userId),
                runningRecord.isStatisticIncluded.isTrue,
                runningRecord.isEnd.isTrue,
                runningRecord.endDatetime.between(start, end)
            ).fetch()
    }
}