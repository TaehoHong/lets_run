package com.example.running.domain.shoe.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.ColumnDefault

@Entity
class Shoe (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "brand", nullable = false, length = 254)
    val brand: String,

    @Column(name = "model", nullable = false, length = 254)
    val model: String,

    @Column(name = "target_distance")
    val targetDistance: Int?,

    @Column(name = "total_distance")
    var totalDistance: Int = 0,

    @ColumnDefault("0")
    @Column(name = "is_main", nullable = false)
    val isMain: Boolean = false,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false,

): BaseDatetime() {

    fun addDistance(distance: Int) {
        totalDistance += distance
    }

    fun store() {
        this.isEnabled = false
    }
}