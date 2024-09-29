package com.example.running.domain.avatar.controller

import com.example.running.domain.avatar.controller.dto.PurchaseItemRequest
import com.example.running.domain.avatar.service.ItemPurchaseService
import com.example.running.domain.avatar.service.UserItemService
import com.example.running.utils.JwtPayloadParser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/user-items")
@RestController
class UserItemController(
    private val userItemService: UserItemService,
    private val itemPurchaseService: ItemPurchaseService,

){

    @PostMapping
    fun purchaseItem(@RequestBody request: PurchaseItemRequest){

        val userId = JwtPayloadParser.getUserId()
        userItemService.verifyUserNotHaveItem(userId, request.itemId)

        itemPurchaseService.purchase(userId, request.itemId)
    }
}