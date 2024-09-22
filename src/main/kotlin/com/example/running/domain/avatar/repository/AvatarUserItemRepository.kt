package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.entity.AvatarUserItem
import com.example.running.domain.avatar.entity.QAvatarUserItem.avatarUserItem
import com.example.running.domain.avatar.entity.QItem.item
import com.example.running.domain.avatar.entity.QItemType.itemType
import com.example.running.domain.avatar.entity.QUserItem.userItem
import com.example.running.domain.avatar.entity.id.AvatarUserItemId
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface AvatarUserItemRepository: JpaRepository<AvatarUserItem, AvatarUserItemId> {

    fun deleteAllByAvatarId(avatarId: Long)
}

@Repository
class AvatarUserItemQueryRepository(private val queryFactory: JPAQueryFactory) {


    fun findAllByAvatarId(avatarId: Long): List<AvatarUserItem> {

        return queryFactory.selectFrom(avatarUserItem)
            .innerJoin(userItem).fetchJoin()
            .innerJoin(item).fetchJoin()
            .innerJoin(itemType).fetchJoin()
            .where(avatarUserItem.id.avatarId.eq(avatarId))
            .fetch()
    }

    fun findAllIdsByAvatarIdAndItemTypeIds(avatarId: Long, itemTypeIds: List<Short>): List<AvatarUserItemId> {

        return queryFactory.select(avatarUserItem.id)
            .from(avatarUserItem)
            .innerJoin(avatarUserItem.userItem, userItem)
            .innerJoin(userItem.item, item)
            .where(
                avatarUserItem.id.avatarId.eq(avatarId),
                item.itemType.id.`in`(itemTypeIds)
            ).fetch()
    }
}
