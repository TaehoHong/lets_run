package com.example.running.domain.user.entity

import com.example.running.domain.common.entity.CreatedDatetime
import com.example.running.domain.common.entity.converter.AuthorityTypeConverter
import com.example.running.domain.common.enums.AuthorityType
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "user")
class User(

    id: Long = 0,

    @Column(name = "nickname", nullable = false, columnDefinition = "VARCHAR(128)")
    var nickname: String,

    @Convert(converter = AuthorityTypeConverter::class)
    @Column(name = "authority_type", nullable = false, columnDefinition = "CHAR(1)")
    val authorityType: AuthorityType,

    @Column(name = "profile_image_url", columnDefinition = "VARCHAR(512)")
    var profileImageUrl: String? = null,

    @ColumnDefault("1")
    @Column(name = "is_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    var isEnabled: Boolean = true,

    @ColumnDefault("0")
    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    var isDeleted: Boolean = false
): CreatedDatetime() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id = id

    constructor(id: Long): this(
        id = id,
        nickname = "",
        authorityType = AuthorityType.USER
    )

    fun updateProfile(nickname: String?, profileImageUrl: String?) {
        nickname?.let { this.nickname = it }
        profileImageUrl?.let { this.profileImageUrl = it }
    }

    fun withdraw() {
        this.isEnabled = false
        this.isDeleted = true
        this.nickname = "탈퇴한 사용자"
        this.profileImageUrl = null
    }
}