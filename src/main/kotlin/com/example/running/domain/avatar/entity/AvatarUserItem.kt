package com.example.running.domain.avatar.entity

import com.example.running.domain.avatar.entity.id.AvatarUserItemId
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId

@Entity
class AvatarUserItem (

    @EmbeddedId
    val id: AvatarUserItemId,

    @MapsId("avatarId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val avatar: Avatar,

    @MapsId("userItemId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false, columnDefinition = "BIGINT UNSIGNED", referencedColumnName = "id")
    val userItem: UserItem
)