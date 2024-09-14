package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.Avatar

class AvatarDto(
    val id: Long,
    val userId: Long,
    val isMain: Boolean,
) {
    constructor(avatar: Avatar): this(
        id = avatar.id,
        userId = avatar.user.id,
        isMain = avatar.isMain
    )
}