package com.example.running.domain.avatar.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemPurchaseService(
    private val userItemService: UserItemService,
    private val itemService: ItemService
) {

    @Transactional(rollbackFor = [Exception::class])
    fun purchase(userId: Long, itemId: Long) {
        itemService.getById(itemId)
            .let {
                userItemService.save(userId, it)
            }.also {
                //TODO 아이템 구매 이력 저장 및 포인트 차감
            }
    }
}