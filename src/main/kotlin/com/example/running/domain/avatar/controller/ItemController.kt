package com.example.running.domain.avatar.controller

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.controller.dto.ItemSearchResponse
import com.example.running.domain.avatar.service.ItemService
import com.example.running.domain.common.dto.CursorResult
import com.example.running.helper.authenticateWithUser
import com.example.running.utils.alsoIfTrue
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
    fun getItems(@ModelAttribute itemSearchRequest: ItemSearchRequest): CursorResult<ItemSearchResponse> {

        true.alsoIfTrue {  }
        return authenticateWithUser { userId ->
            itemService.getItemDtoPage(userId, itemSearchRequest)
                .of { ItemSearchResponse(it) }
        }
    }
}