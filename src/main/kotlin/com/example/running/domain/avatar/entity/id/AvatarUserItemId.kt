package com.example.running.domain.avatar.entity.id

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.springframework.core.serializer.Serializer
import java.io.Serializable

@Embeddable
class AvatarUserItemId (

    @Column(name = "avatar_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val avatarId: Long,

    @Column(name = "user_item_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val userItemId: Long

): Serializable