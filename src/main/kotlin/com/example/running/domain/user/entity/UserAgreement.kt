package com.example.running.domain.user.entity

import com.example.running.domain.term.entity.Term
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Entity
@Table(name = "user_agreement")
class UserAgreement(
    user: User,
    term: Term,
    isAgreed: Boolean,
    agreedDatetime: OffsetDateTime? = null,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user = user

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    val term = term

    @Column(name = "is_agreed", nullable = false)
    var isAgreed = isAgreed
        private set

    @Column(name = "agreed_datetime", nullable = false)
    var agreedDatetime = agreedDatetime
        private set


    fun agree() {
        this.isAgreed = true
        this.agreedDatetime = OffsetDateTime.now(ZoneOffset.UTC)
    }
}