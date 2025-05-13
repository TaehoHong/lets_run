package com.example.running.domain.point.entity

import com.example.running.common.entity.CreatedDatetime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity(name = "user_point_history")
class UserPointHistory(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_type_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val pointType: PointType,

    @Column(name = "point", nullable = false, columnDefinition = "INT UNSIGNED")
    val point: Int = 0,

    @Column(name = "running_record_id", nullable = true, columnDefinition = "BIGINT UNSIGNED")
    val runningRecordId: Long? = null,

    @Column(name = "item_id", nullable = true, columnDefinition = "BIGINT UNSIGNED")
    val itemId: Long? = null,

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    val isDeleted: Boolean = false

): CreatedDatetime() {

    constructor(userId: Long, pointTypeId: Short, point: Int, runningRecordId: Long?, itemId: Long?) : this(
        userId = userId,
        point = point,
        pointType = PointType(pointTypeId),
        runningRecordId = runningRecordId,
        itemId = itemId,
    )
}