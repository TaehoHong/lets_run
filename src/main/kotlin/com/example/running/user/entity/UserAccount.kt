package com.example.running.user.entity

import com.example.running.common.entity.CreateDateTime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "user_account")
class UserAccount(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_type_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    val accountType: AccountType,

    @Column(name = "email", nullable = false, columnDefinition = "VARCHAR(128)")
    val email: String,

    @Column(name = "password", nullable = false, columnDefinition = "VARCHAR(256)")
    val password: String,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    val isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    val isDeleted: Boolean = false,
): CreateDateTime() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0
}