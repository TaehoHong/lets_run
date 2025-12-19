package com.example.running.domain.user.repository


import com.example.running.domain.avatar.entity.QAvatar.Companion.avatar
import com.example.running.domain.avatar.entity.QAvatarUserItem.Companion.avatarUserItem
import com.example.running.domain.avatar.entity.QItem.Companion.item
import com.example.running.domain.avatar.entity.QItemType.Companion.itemType
import com.example.running.domain.avatar.entity.QUserItem.Companion.userItem
import com.example.running.domain.point.entity.QUserPoint.Companion.userPoint
import com.example.running.domain.running.entity.QRunningRecord.Companion.runningRecord
import com.example.running.domain.user.dto.EquippedItemDto
import com.example.running.domain.user.dto.UserAccountDataDto
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.entity.QAccountType.Companion.accountType
import com.example.running.domain.user.entity.QUser.Companion.user
import com.example.running.domain.user.entity.QUserAccount.Companion.userAccount
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.service.dto.UserDto
import com.example.running.utils.ifNotEmpty
import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.core.group.GroupBy.set
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long>, QUserRepository


interface QUserRepository {
    fun getUserDataDtoById(id: Long): UserDataDto
    fun findUserDto(id: Long): UserDto?
}

@Repository
class QUserRepositoryImpl(private val queryFactory: JPAQueryFactory) : QUserRepository {

    override fun getUserDataDtoById(id: Long): UserDataDto {
        return queryFactory.from(user)
            .innerJoin(userAccount)
            .on(userAccount.user.id.eq(user.id).and(userAccount.isEnabled.isTrue).and(userAccount.isDeleted.isFalse))
            .innerJoin(userAccount.accountType, accountType)
            .innerJoin(userPoint).on(userPoint.user.id.eq(user.id))
            .innerJoin(avatar).on(avatar.user.id.eq(user.id).and(avatar.isMain.isTrue))
            .where(user.id.eq(id).and(user.isEnabled.isTrue).and(user.isDeleted.isFalse))
            .transform(
                groupBy(user.id).list(
                    Projections.constructor(
                        UserDataDto::class.java,
                        user.id,
                        user.nickname,
                        user.profileImageUrl,
                        user.authorityType,
                        userPoint.point,
                        set(
                            Projections.constructor(
                                UserAccountDataDto::class.java,
                                userAccount.id,
                                userAccount.email,
                                accountType.name
                            )
                        ),
                        avatar.id,
                        JPAExpressions.select(Expressions.TRUE)
                            .from(runningRecord)
                            .where(
                                runningRecord.user.id.eq(user.id),
                                runningRecord.isStatisticIncluded.eq(true)
                            ).exists(),
                        avatar.hairColor
                    )
                )
            )[0].also { dto ->

            queryFactory.select(
                Projections.constructor(
                    EquippedItemDto::class.java,
                    item.id,
                    item.itemType.id,
                    item.name,
                    item.filePath,
                    item.unityFilePath
                )
            ).from(avatar)
                .innerJoin(avatarUserItem).on(avatarUserItem.avatar.id.eq(avatar.id))
                .innerJoin(avatarUserItem.userItem, userItem)
                .innerJoin(userItem.item, item)
                .innerJoin(item.itemType, itemType)
                .where(
                    avatar.user.id.eq(dto.id),
                    avatar.isMain.isTrue
                ).fetch().ifNotEmpty { items ->
                    dto.equippedItems.addAll(items)
                }
        }
    }

    override fun findUserDto(id: Long): UserDto? {
        return queryFactory.select(
            Projections.constructor(
                UserDto::class.java,
                user.id,
                user.nickname,
                user.authorityType
            )
        ).from(user)
            .where(
                user.id.eq(id),
                user.isEnabled.isTrue,
                user.isDeleted.isFalse
            )
            .fetchOne()
    }

}