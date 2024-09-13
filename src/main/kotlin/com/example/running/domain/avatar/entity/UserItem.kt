package com.example.running.domain.avatar.entity

import com.example.running.common.entity.CreateDateTime
import com.example.running.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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

) : CreateDateTime()