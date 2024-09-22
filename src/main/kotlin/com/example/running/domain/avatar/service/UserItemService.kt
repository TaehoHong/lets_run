package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.entity.UserItem
import com.example.running.domain.avatar.repository.UserItemQueryRepository
import com.example.running.domain.avatar.repository.UserItemRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserItemService(
    private val userItemRepository: UserItemRepository,
    private val userItemQueryRepository: UserItemQueryRepository
) {

    @Transactional(readOnly = true)
    fun verifyUserNotHaveItem(userId: Long, itemId: Long) {
        userItemRepository.existsByUserIdAndItemIdAndIsEnabledAndIsExpired(userId, itemId)
            .alsoIfTrue { throw RuntimeException("이미 보유한 아이템입니다.") }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun save(userId: Long, item: Item): UserItem {
        return userItemRepository.save(UserItem(userId = userId, item = item))
    }

    @Transactional(readOnly = true)
    fun getAllByItemIds(itemIds: List<Long>): List<UserItem> {
        return userItemQueryRepository.findAllByItemIdIn(itemIds)
    }
}