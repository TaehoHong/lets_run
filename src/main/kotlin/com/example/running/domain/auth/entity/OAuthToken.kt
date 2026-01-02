package com.example.running.domain.auth.entity

import com.example.running.domain.common.entity.BaseDatetime
import com.example.running.domain.user.entity.UserAccount
import jakarta.persistence.*

@Entity
@Table(name = "oauth_token")
class OAuthToken(

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userAccount: UserAccount,

    @Column(name = "refresh_token", nullable = true, columnDefinition = "VARCHAR(512)")
    var refreshToken: String? = null,

) : BaseDatetime() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0

    fun updateRefreshToken(refreshToken: String?) {
        this.refreshToken = refreshToken
    }
}
