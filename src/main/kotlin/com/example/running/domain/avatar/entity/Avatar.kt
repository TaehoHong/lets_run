package com.example.running.domain.avatar.entity

import com.example.running.domain.common.entity.User
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
    val orderIndex: Short
) {
    constructor(id: Long): this(
        id = id,
        user = User(0),
        isMain = true,
        orderIndex = 0
    )
}