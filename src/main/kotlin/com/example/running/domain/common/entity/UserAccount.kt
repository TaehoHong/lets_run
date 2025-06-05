package com.example.running.domain.common.entity

import jakarta.persistence.*
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
    val password: String? = null,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    val isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    val isDeleted: Boolean = false,
): CreatedDatetime() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0

    constructor(userId: Long, accountTypeId: Short, email: String, password: String?): this(
        user = User(id = userId),
        accountType = AccountType(id = accountTypeId),
        email = email,
        password = password
    )
}