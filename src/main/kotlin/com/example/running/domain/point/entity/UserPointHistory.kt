package com.example.running.domain.point.entity

import com.example.running.common.entity.CreatedDatetime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity(name = "user_point_history")
class UserPointHistory(

    @Id @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userId: Long,

    @Column(name = "point_type_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val pointTypeId: Short,

    @Column(name = "point", nullable = false, columnDefinition = "INT UNSIGNED")
    val point: Int = 0,

    @Column(name = "running_record_id", nullable = true, columnDefinition = "BIGINT UNSIGNED")
    val runningRecordId: Long? = null,

    @Column(name = "item_id", nullable = true, columnDefinition = "BIGINT UNSIGNED")
    val itemId: Long? = null,

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    val isDeleted: Boolean = false

): CreatedDatetime()