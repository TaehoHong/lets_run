package com.example.running.domain.running.entity

import com.example.running.common.entity.CreatedDatetime
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
class RunningRecordItem(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "running_record_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val runningRecord: RunningRecord,

    @Column(name = "distance", nullable = false, columnDefinition = "INT UNSIGNED")
    val distance: Int = 0,

    @Column(name = "duration_sec", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val durationSec: Long = 0,

    @Column(name = "cadence", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val cadence: Short = 0,

    @Column(name = "heart_rate", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val heartRate: Short = 0,

    @Column(name = "min_heart_rate", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val minHeartRate: Short = 0,

    @Column(name = "max_heart_rate", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val maxHeartRate: Short = 0,

    @Column(name = "order_index", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    val orderIndex: Short,

    @Column(name = "start_datetime", nullable = false, columnDefinition = "DATETIME")
    val startDatetime: OffsetDateTime,

    @Column(name = "end_datetime",nullable = false, columnDefinition = "DATETIME")
    val endDatetime: OffsetDateTime

): CreatedDatetime()