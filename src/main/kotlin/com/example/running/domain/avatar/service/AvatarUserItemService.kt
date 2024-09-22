package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.AvatarUserItem
import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.repository.AvatarUserItemQueryRepository
import com.example.running.domain.avatar.repository.AvatarUserItemRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AvatarUserItemService(
    private val userItemService: UserItemService,
    private val avatarUserItemRepository: AvatarUserItemRepository,
    private val avatarUserItemQueryRepository: AvatarUserItemQueryRepository
) {

    @Transactional(readOnly = true)
    fun getAllByAvatarId(avatarId: Long): List<AvatarUserItem> {
        return avatarUserItemQueryRepository.findAllByAvatarId(avatarId)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun deleteAllByAvatarId(avatarId: Long) {
        avatarUserItemRepository.deleteAllByAvatarId(avatarId)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun saveAll(avatarId: Long, itemIds: List<Long>) {

        userItemService.getAllByItemIds(itemIds)
            .also {
                verifyUserHaveItem(itemIds, it.map { it.item.id })
                verifyItemTypeNotDuplicated(it.map { it.item })
            }
            .map { AvatarUserItem(avatarId, it) }
            .let { avatarUserItemRepository.saveAll(it) }
    }

    private fun verifyUserHaveItem(inputItemIds: List<Long>, havingItemIds: List<Long>) {
        inputItemIds.map { havingItemIds.contains(it) }
            .contains(false)
            .alsoIfTrue { throw IllegalArgumentException("보유하지 않은 아이템이 존재합니다.") }
    }

    private fun verifyItemTypeNotDuplicated(items: List<Item>) {
        items.map { it.itemType.id }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .isNotEmpty()
            .alsoIfTrue { throw IllegalArgumentException("중복된 타입이 존재합니다.")}
    }
}