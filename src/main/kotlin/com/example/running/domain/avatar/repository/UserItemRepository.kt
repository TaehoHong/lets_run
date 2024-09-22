package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.entity.UserItem
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface UserItemRepository: JpaRepository<UserItem, Long> {

    fun  existsByUserIdAndItemIdAndIsEnabledAndIsExpired(userId: Long, itemId: Long, isEnabled: Boolean = true, isExpired: Boolean = false): Boolean
}

@Repository
class UserItemQueryRepository(private val queryFactory: JPAQueryFactory) {

}