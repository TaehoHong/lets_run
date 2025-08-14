package com.example.running.domain.shoe.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault


@Entity
class Shoe (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "brand", nullable = false, length = 254)
    var brand: String,

    @Column(name = "model", nullable = false, length = 254)
    var model: String,

    @Column(name = "target_distance")
    var targetDistance: Int?,

    @Column(name = "total_distance")
    var totalDistance: Int = 0,

    @ColumnDefault("0")
    @Column(name = "is_main", nullable = false)
    var isMain: Boolean = false,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

): BaseDatetime() {

    constructor(id: Long): this(
        id = id,
        user = User(id = 0),
        brand = "",
        model = "",
        targetDistance = null
    )

    fun addDistance(distance: Int) {
        totalDistance += distance
    }

    fun update(
        brand: String? = null,
        model: String? = null,
        targetDistance: Int? = null,
        isMain: Boolean? = null,
        isEnabled: Boolean? = null,
        isDeleted: Boolean? = null,
    ) {
        brand?.let { this.brand = it }
        model?.let { this.model = it }
        targetDistance?.let { this.targetDistance = it }
        isMain?.let { this.isMain = it }
        isEnabled?.let { this.isEnabled = it }
        isDeleted?.let { this.isDeleted = it }
    }
}