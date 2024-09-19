package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.Item
import com.querydsl.core.annotations.QueryProjection

class ItemDto (
    val id: Long,
    val itemTypeDto: ItemTypeDto,
    val name: String,
    val filePath: String,
    val point: Int
) {
    @QueryProjection constructor(item: Item): this(
        id = item.id,
        name = item.name,
        filePath = item.filePath,
        point = item.point,
        itemTypeDto = ItemTypeDto(item.itemType)
    )
}