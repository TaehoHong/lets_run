package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.AvatarItemDto

class AvatarItemResponse(
    val id: Long,
    val itemType: ItemTypeResponse,
    val name: String,
    val filePath: String,
    val unityFilePath: String,
    val isOwned: Boolean = true,
    val point: Int = 0,
) {
    constructor(avatarItemDto: AvatarItemDto): this (
        id = avatarItemDto.itemId,
        itemType = ItemTypeResponse(avatarItemDto.itemTypeDto),
        name = avatarItemDto.name,
        filePath = avatarItemDto.filePath,
        unityFilePath = avatarItemDto.unityFilePath
    )
}