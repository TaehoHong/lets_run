package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.entity.UserItem
import com.example.running.domain.avatar.repository.UserItemRepository
import com.example.running.utils.ifNotEmpty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserItemService(
    private val userItemRepository: UserItemRepository,
) {

    @Transactional(readOnly = true)
    fun verifyUserNotHaveItems(userId: Long, itemIds: List<Long>) {
        userItemRepository.findAllItemIdByUserIdAndItemIds(userId, itemIds)
            .ifNotEmpty { throw RuntimeException("이미 보유한 아이템입니다.") }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun saveAll(userId: Long, items: List<Item>): List<UserItem> {

        return items.map {
            UserItem(userId = userId, item = it)
        }.let {
            userItemRepository.saveAll(it)
        }
    }

    @Transactional(readOnly = true)
    fun getAllByItemIds(itemIds: List<Long>): List<UserItem> {
        return userItemRepository.findAllByItemIdIn(itemIds)
    }
}