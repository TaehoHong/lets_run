package com.example.running.domain.running.repository

import com.example.running.domain.running.entity.RunningRecordItem
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

interface RunningRecordItemRepository: JpaRepository<RunningRecordItem, Long>, RunningRecordItemJdbcRepository {
}

@Repository
class RunningRecordItemQueryRepository(
    private val queryFactory: JPAQueryFactory
) {
}

interface RunningRecordItemJdbcRepository {
    fun saveInBatch(records: List<RunningRecordItem>)
}

@Repository
class RunningRecordItemJdbcRepositoryImpl(
    private val jdbcTemplate: JdbcTemplate
) :RunningRecordItemJdbcRepository {

    override fun saveInBatch(records: List<RunningRecordItem>) {
        if (records.isEmpty()) return
        
        // 500개씩 분할하여 배치 삽입
        records.chunked(500).forEach { chunk ->
            batchInsert(chunk)
        }
    }

    private fun batchInsert(records: List<RunningRecordItem>) {
        val sql = """
            INSERT INTO running_record_item(
                running_record_id, distance, duration_sec, cadence, heart_rate, 
                min_heart_rate, max_heart_rate, order_index, start_datetime, 
                end_datetime
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val batchArgs = records.map { record ->
            arrayOf(
                record.runningRecord.id,
                record.distance,
                record.durationSec,
                record.cadence,
                record.heartRate,
                record.minHeartRate,
                record.maxHeartRate,
                record.orderIndex,
                record.startDatetime,
                record.endDatetime
            )
        }

        jdbcTemplate.batchUpdate(sql, batchArgs)
    }
}