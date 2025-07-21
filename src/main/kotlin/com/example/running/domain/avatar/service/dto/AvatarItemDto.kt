package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.AvatarUserItem

class AvatarItemDto(
    val itemId: Long,
    val name: String,
    val filePath: String,
    val unityFilePath: String,
    val itemTypeDto: ItemTypeDto,
) {
    constructor(avatarUserItem: AvatarUserItem): this(
        itemId = avatarUserItem.userItem.item.id,
        itemTypeDto = ItemTypeDto(avatarUserItem.userItem.item.itemType),
        name = avatarUserItem.userItem.item.name,
        filePath = avatarUserItem.userItem.item.filePath,
        unityFilePath = avatarUserItem.userItem.item.unityFilePath,
    )
}