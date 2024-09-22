package com.example.running.domain.avatar.controller

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.controller.dto.ItemSearchResponse
import com.example.running.domain.avatar.service.ItemService
import com.example.running.domain.common.dto.PageResult
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/items")
@RestController
class ItemController(
    private val itemService: ItemService
) {

    @GetMapping
    fun getItems(@ModelAttribute itemSearchRequest: ItemSearchRequest, pageable: Pageable): PageResult<ItemSearchResponse> {
        return itemService.getItemDtoPage(itemSearchRequest, pageable)
            .let {
                PageResult.of(it) { item -> ItemSearchResponse(item) }
            }
    }
}