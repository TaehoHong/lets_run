package com.example.running.user.entity

import com.example.running.common.entity.CreatedDatetime
import com.example.running.user.entity.converter.AuthorityTypeConverter
import com.example.running.user.enums.AuthorityType
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "user")
class User(

    id: Long = 0,

    @Column(name = "nickname", nullable = false, columnDefinition = "VARCHAR(128)")
    val nickname: String,

    @Convert(converter = AuthorityTypeConverter::class)
    @Column(name = "authority_type", nullable = false, columnDefinition = "CHAR(1)")
    val authorityType: AuthorityType,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    val isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    val isDeleted: Boolean = false
): CreatedDatetime() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id = id

    constructor(id: Long): this(
        id = id,
        nickname = "",
        authorityType = AuthorityType.USER
    )
}