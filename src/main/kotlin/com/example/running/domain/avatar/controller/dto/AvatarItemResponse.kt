package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.AvatarItemDto

class AvatarItemResponse(
    val itemId: Long,
    val itemType: ItemTypeResponse,
    val name: String,
    val filePath: String
) {
    constructor(avatarItemDto: AvatarItemDto): this (
        itemId = avatarItemDto.itemId,
        itemType = ItemTypeResponse(avatarItemDto.itemTypeDto),
        name = avatarItemDto.name,
        filePath = avatarItemDto.filePath
    )
}