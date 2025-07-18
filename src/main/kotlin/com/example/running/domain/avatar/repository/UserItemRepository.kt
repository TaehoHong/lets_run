package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.entity.QUserItem.Companion.userItem
import com.example.running.domain.avatar.entity.UserItem
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface UserItemRepository: JpaRepository<UserItem, Long>, QUserItemRepository {

}
interface QUserItemRepository {
    fun findAllItemIdByUserIdAndItemIds(userId: Long, itemIds: List<Long>): List<Long>
    fun findAllByItemIdIn(itemIds: List<Long>): List<UserItem>
}


@Repository
class QUserItemRepositoryImpl(private val queryFactory: JPAQueryFactory): QUserItemRepository {

    override fun findAllItemIdByUserIdAndItemIds(userId: Long, itemIds: List<Long>): List<Long> {
        return queryFactory.select(userItem.item.id)
            .from(userItem)
            .where(userItem.user.id.eq(userId),
                userItem.item.id.`in`(itemIds),
                userItem.isEnabled.isTrue,
                userItem.isExpired.isFalse
            ).fetch()
    }

    override fun findAllByItemIdIn(itemIds: List<Long>): List<UserItem> {
        return queryFactory.selectFrom(userItem)
            .innerJoin(userItem.item).fetchJoin()
            .where(userItem.item.id.`in`(itemIds))
            .fetch()
    }
}