package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.ItemDto

class ItemSearchRequest(
    val cursor: Long? = null,
    val itemTypeId: Short? = null,
    val excludeMyItems: Boolean = false,
    val size: Int = 30
)

class ItemSearchResponse(
    val id: Long,
    val itemType: ItemTypeResponse,
    val name: String,
    val filePath: String,
    val unityFilePath: String,
    val point: Int,
    val isOwned: Boolean = false,
) {
    constructor(item: ItemDto): this(
        id = item.id,
        name = item.name,
        filePath = item.filePath,
        unityFilePath = item.unityFilePath,
        itemType = ItemTypeResponse(item.itemTypeDto),
        point = item.point,
        isOwned = item.isOwnedByUser
    )
}