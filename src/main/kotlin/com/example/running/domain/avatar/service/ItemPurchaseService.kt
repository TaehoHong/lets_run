package com.example.running.domain.avatar.service

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
    fun purchase(userId: Long, itemIds: List<Long>) {
        itemService.getAllByIds(itemIds)
            .also {
                val totalPoint = it.sumOf { it.point }
                userPointService.verifyPoint(userId, totalPoint)
            }.let {
                userItemService.saveAll(userId, it)
            }.forEach {
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