package com.example.running.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table


@Entity
@Table(name = "account_type")
class AccountType (
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "TINYINT")
    val id: Short,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(32)")
    val name: String
) {
    constructor(id: Short): this(
        id = id,
        name = ""
    )
}