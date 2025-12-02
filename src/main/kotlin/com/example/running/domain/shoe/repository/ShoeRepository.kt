package com.example.running.domain.shoe.repository

import com.example.running.domain.shoe.entity.QShoe.Companion.shoe
import com.example.running.domain.shoe.entity.Shoe
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ShoeRepository: JpaRepository<Shoe, Long>, QShoeRepository {
    fun findShoeByUserIdAndIsMain(userId: Long, main: Boolean): Shoe?
}


interface QShoeRepository {
    fun findAll(userId: Long, isEnabled: Boolean?, cursor: Long?, size: Int): List<Shoe>
    fun hasNext(userId: Long, isEnabled: Boolean?, id: Long): Boolean
    fun updateIsMainByUserId(userId: Long, isMain: Boolean): Long
    fun updateIsMainByIdAndUserId(id:Long, userId: Long, isMain: Boolean): Long
}


@Repository
class QShoeRepositoryImpl(private val queryFactory: JPAQueryFactory): QShoeRepository {

    override fun findAll(userId: Long, isEnabled: Boolean?, cursor: Long?, size: Int): List<Shoe> {
        return queryFactory.selectFrom(shoe)
            .where(getBooleanBuilder(userId, isEnabled, cursor))
            .orderBy(shoe.id.desc())
            .fetch()
    }

    override fun hasNext(userId: Long, isEnabled: Boolean?, id: Long): Boolean {
        return queryFactory.select(Expressions.TRUE)
            .from(shoe)
            .where(getBooleanBuilder(userId, isEnabled, id))
            .orderBy(shoe.id.desc())
            .fetchOne() ?: false
    }


    private fun getBooleanBuilder(userId: Long, isEnabled: Boolean?, id: Long?): BooleanBuilder {
        return BooleanBuilder(
            shoe.user.id.eq(userId).and(shoe.isDeleted.eq(false))
        ).apply {
            id?.let { and(shoe.id.lt(it)) }
            isEnabled?.let { and(shoe.isEnabled.eq(it)) }
        }
    }

    override fun updateIsMainByUserId(userId: Long, isMain: Boolean): Long {
        return queryFactory.update(shoe)
            .set(shoe.isMain, isMain)
            .where(shoe.user.id.eq(userId))
            .execute()
    }

    override fun updateIsMainByIdAndUserId(id: Long, userId: Long, isMain: Boolean): Long {
        return queryFactory.update(shoe)
            .set(shoe.isMain, isMain)
            .where(
                shoe.id.eq(id),
                shoe.user.id.eq(userId)
            ).execute()
    }
}