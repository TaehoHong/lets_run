package com.example.running.domain.avatar.entity

import com.example.running.domain.user.entity.User
import jakarta.persistence.*

@Entity
class Avatar (

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val user: User,

    @Column(name = "is_main", nullable = false, columnDefinition = "TINYINT(1)")
    val isMain: Boolean,

    @Column(name = "order_index", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val orderIndex: Short,

    @Column(name = "hair_color", nullable = false, length = 7, columnDefinition = "VARCHAR(7)")
    var hairColor: String = "#8B4513"
) {
    constructor(id: Long): this(
        id = id,
        user = User(0),
        isMain = true,
        orderIndex = 0,
        hairColor = "#8B4513"
    )
}