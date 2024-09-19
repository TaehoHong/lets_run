package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.ItemDto

class ItemSearchRequest(
    val itemTypeId: Short? = null
)

class ItemSearchResponse(
    val id: Long,
    val itemType: ItemTypeResponse,
    val name: String,
    val filePath: String,
    val point: Int
) {
    constructor(item: ItemDto): this(
        id = item.id,
        name = item.name,
        filePath = item.filePath,
        itemType = ItemTypeResponse(item.itemTypeDto),
        point = item.point
    )
}