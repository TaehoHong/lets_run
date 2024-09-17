package com.example.running.domain.avatar.controller.dto

import com.example.running.domain.avatar.service.dto.ItemTypeDto

class ItemTypeResponse(
    val id: Short,
    val name: String
) {
    constructor(itemTypeDto: ItemTypeDto) : this(
        id = itemTypeDto.id,
        name = itemTypeDto.name
    )
}