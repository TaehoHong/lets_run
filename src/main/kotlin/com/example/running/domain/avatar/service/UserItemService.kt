package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.entity.UserItem
import com.example.running.domain.avatar.repository.UserItemRepository
import com.example.running.utils.alsoIfTrue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserItemService(
    private val userItemRepository: UserItemRepository
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
}