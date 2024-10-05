package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.entity.QItem.item
import com.example.running.domain.avatar.entity.QItemType.itemType
import com.example.running.domain.avatar.entity.QUserItem.userItem
import com.example.running.domain.avatar.service.dto.ItemDto
import com.example.running.domain.avatar.service.dto.QItemDto
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ItemRepository: JpaRepository<Item, Long> {
}

@Repository
class ItemQueryRepository(
    private val queryFactory: JPAQueryFactory
) {

    fun findItemDtoPage(userId: Long, itemSearchRequest: ItemSearchRequest, pageable: Pageable): Page<ItemDto> {
        return PageImpl(
            findAllItems(userId, itemSearchRequest, pageable),
            pageable,
            countAllItems(userId, itemSearchRequest)
        )
    }

    private fun findAllItems(userId: Long, itemSearchRequest: ItemSearchRequest, pageable: Pageable): List<ItemDto> {

        return queryFactory
            .select(QItemDto(item, userItem.isNotNull))
            .from(item)
            .innerJoin(item.itemType, itemType).fetchJoin()
            .leftJoin(userItem).on(userItem.item.id.eq(item.id).and(userItem.user.id.eq(userId)))
            .where(getWhereClause(itemSearchRequest))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
    }



    private fun countAllItems(userId: Long, itemSearchRequest: ItemSearchRequest): Long {
        return queryFactory
            .select(item.count())
            .from(item)
            .innerJoin(item.itemType, itemType)
            .leftJoin(userItem).on(userItem.item.id.eq(item.id).and(userItem.user.id.eq(userId)))
            .where(getWhereClause(itemSearchRequest))
            .fetchFirst()?: 0
    }

    private fun getWhereClause(itemSearchRequest: ItemSearchRequest): BooleanBuilder {
        return BooleanBuilder().apply {

            if(itemSearchRequest.excludeMyItems) {
                this.and(userItem.isNull)
            }

            itemSearchRequest.itemTypeId?.let {
                this.and(item.itemType.id.eq(it))
            }
        }
    }

    fun findAllItemTypeIdByIdIn(ids: List<Long>): List<Short> {
        return queryFactory.select(item.itemType.id)
            .from(item)
            .where(item.id.`in`(ids))
            .fetch()
    }
}