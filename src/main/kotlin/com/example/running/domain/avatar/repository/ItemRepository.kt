package com.example.running.domain.avatar.repository

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.entity.QItem.Companion.item
import com.example.running.domain.avatar.entity.QItemType.Companion.itemType
import com.example.running.domain.avatar.entity.QUserItem.Companion.userItem
import com.example.running.domain.avatar.service.dto.ItemDto
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ItemRepository: JpaRepository<Item, Long>, QItemRepository {
}


interface QItemRepository {
    fun findAllItemDtos(userId: Long, cursor: Long?, itemSearchRequest: ItemSearchRequest): List<ItemDto>
    fun hasNext(userId: Long, cursor: Long?, itemSearchRequest: ItemSearchRequest): Boolean
    fun findAllItemTypeIdByIdIn(ids: List<Long>): List<Short>
}

@Repository
class QItemRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : QItemRepository {

    override fun findAllItemDtos(userId: Long, cursor: Long?, itemSearchRequest: ItemSearchRequest): List<ItemDto> {

        return queryFactory
            .select(
                Projections.constructor(
                    ItemDto::class.java,
                item,
                    userItem.isNotNull
                )
            ).from(item)
            .innerJoin(item.itemType, itemType).fetchJoin()
            .leftJoin(userItem).on(userItem.item.id.eq(item.id).and(userItem.user.id.eq(userId)))
            .where(getWhereClause(cursor, itemSearchRequest))
            .fetch()
    }

    override fun hasNext(userId: Long, cursor: Long?, itemSearchRequest: ItemSearchRequest): Boolean {
        return queryFactory
            .select(Expressions.TRUE)
            .from(item)
            .innerJoin(item.itemType, itemType)
            .leftJoin(userItem).on(userItem.item.id.eq(item.id).and(userItem.user.id.eq(userId)))
            .where(getWhereClause(cursor, itemSearchRequest))
            .fetchFirst()?: false
    }

    private fun getWhereClause(cursor: Long?, itemSearchRequest: ItemSearchRequest): BooleanBuilder {
        return BooleanBuilder().apply {

            if(itemSearchRequest.excludeMyItems) {
                this.and(userItem.isNull)
            }

            itemSearchRequest.itemTypeId?.let {
                this.and(item.itemType.id.eq(it))
            }

            cursor?.also {
                this.and(item.id.gt(it))
            }
        }
    }

    override fun findAllItemTypeIdByIdIn(ids: List<Long>): List<Short> {
        return queryFactory.select(item.itemType.id)
            .from(item)
            .where(item.id.`in`(ids))
            .fetch()
    }
}