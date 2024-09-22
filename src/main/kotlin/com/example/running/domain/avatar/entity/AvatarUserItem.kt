package com.example.running.domain.avatar.entity

import com.example.running.domain.avatar.entity.id.AvatarUserItemId
import jakarta.persistence.*

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
) {
    constructor(avatarId: Long, userItem: UserItem): this
    (
        id = AvatarUserItemId(avatarId = avatarId, userItemId = userItem.id),
        avatar = Avatar(id = avatarId),
        userItem = userItem
    )
}