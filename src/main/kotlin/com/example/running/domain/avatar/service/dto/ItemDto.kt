package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.Item
import com.querydsl.core.annotations.QueryProjection

class ItemDto (
    val id: Long,
    val itemTypeDto: ItemTypeDto,
    val name: String,
    val filePath: String,
    val point: Int,
    val isOwnedByUser: Boolean
) {
    @QueryProjection constructor(item: Item, isOwnedByUser: Boolean): this(
        id = item.id,
        name = item.name,
        filePath = item.filePath,
        point = item.point,
        itemTypeDto = ItemTypeDto(item.itemType),
        isOwnedByUser = isOwnedByUser
    )
}