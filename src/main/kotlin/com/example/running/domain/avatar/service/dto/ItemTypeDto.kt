package com.example.running.domain.avatar.service.dto

import com.example.running.domain.avatar.entity.ItemType

class ItemTypeDto(
    val id: Short,
    val name: String,
) {

    constructor(itemType: ItemType) : this(
        id = itemType.id,
        name = itemType.name
    )
}