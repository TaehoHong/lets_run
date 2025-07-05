package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.Item

class ItemDto (
    val id: Long,
    val itemTypeDto: ItemTypeDto,
    val name: String,
    val filePath: String,
    val unityFilePath: String,
    val point: Int,
    val isOwnedByUser: Boolean
) {
    constructor(item: Item, isOwnedByUser: Boolean): this(
        id = item.id,
        name = item.name,
        filePath = item.filePath,
        unityFilePath = item.unityFilePath,
        point = item.point,
        itemTypeDto = ItemTypeDto(item.itemType),
        isOwnedByUser = isOwnedByUser
    )
}