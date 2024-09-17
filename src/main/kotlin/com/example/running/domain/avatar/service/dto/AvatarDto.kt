package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.Avatar
import com.example.running.domain.avatar.entity.AvatarUserItem

class AvatarDto(
    val id: Long,
    val userId: Long,
    val isMain: Boolean,
    val avatarItemDtoList: List<AvatarItemDto>
) {
    constructor(avatar: Avatar, avatarUserItems: List<AvatarUserItem>): this(
        id = avatar.id,
        userId = avatar.user.id,
        isMain = avatar.isMain,
        avatarUserItems.map { AvatarItemDto(it) }
    )
}