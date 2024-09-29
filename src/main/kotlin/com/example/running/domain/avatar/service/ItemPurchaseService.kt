package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.UserItem
import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemPurchaseService(
    private val userItemService: UserItemService,
    private val itemService: ItemService,
    private val userPointService: UserPointService
) {

    @Transactional(rollbackFor = [Exception::class])
    fun purchase(userId: Long, itemId: Long): UserItem {
        return itemService.getById(itemId)
            .also {
                userPointService.verifyPoint(userId, it.point)
            }.let {
                userItemService.save(userId, it)
            }.also {
                userPointService.updatePoint(
                    PointUsageDto(
                        userId = userId,
                        point = -it.item.point,
                        pointTypeId = PointTypeName.PURCHASE_ITEM.id,
                        itemId = it.item.id
                    )
                )
            }
    }
}