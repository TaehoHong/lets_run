package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.AvatarDto

class AvatarResponse(
    val id: Long,
    val userId: Long,
    val isMain: Boolean,
    val hairColor: String,
    val avatarItems: List<AvatarItemResponse>
) {

    constructor(avatarDto: AvatarDto) : this(
        id = avatarDto.id,
        userId = avatarDto.userId,
        isMain = avatarDto.isMain,
        hairColor = avatarDto.hairColor,
        avatarDto.avatarItemDtoList.map { AvatarItemResponse(it) }
    )
}