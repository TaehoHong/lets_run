package com.example.running.domain.avatar.entity

import com.example.running.domain.common.entity.CreatedDatetime
import com.example.running.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.OffsetDateTime

@Entity
class UserItem (

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val item: Item,

    @Column(name = "is_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    val isEnabled: Boolean,

    @ColumnDefault("0")
    @Column(name = "is_expired", nullable = false, columnDefinition = "TINYINT(1)")
    val isExpired: Boolean,

    @Column(name = "expire_datetime", columnDefinition = "DATETIME")
    val expireDateTime: OffsetDateTime? = null

) : CreatedDatetime() {

    constructor(userId: Long, item: Item) : this(
        user = User(id = userId),
        item = item,
        isEnabled = true,
        isExpired = false
    )
}