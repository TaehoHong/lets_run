package com.example.running.domain.point.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity(name = "point_type")
class PointType(

    @Id @Column(name = "id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val id: Short,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(20)")
    val name: String,

) {
    constructor(id: Short): this(
        id = id,
        name = ""
    )
}