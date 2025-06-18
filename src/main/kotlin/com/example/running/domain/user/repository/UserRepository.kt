package com.example.running.domain.user.repository


import com.example.running.domain.avatar.entity.QAvatar.Companion.avatar
import com.example.running.domain.avatar.entity.QAvatarUserItem.Companion.avatarUserItem
import com.example.running.domain.avatar.entity.QItem.Companion.item
import com.example.running.domain.avatar.entity.QItemType.Companion.itemType
import com.example.running.domain.avatar.entity.QUserItem.Companion.userItem
import com.example.running.domain.point.entity.QUserPoint.Companion.userPoint
import com.example.running.domain.user.dto.EquippedItemDto
import com.example.running.domain.user.dto.UserAccountDataDto
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.entity.QUser.Companion.user
import com.example.running.domain.user.entity.QUserAccount.Companion.userAccount
import com.example.running.domain.user.entity.User
import com.querydsl.core.QueryFactory
import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.core.types.ExpressionUtils.list
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository: JpaRepository<User, Long>, QUserRepository {
}


interface QUserRepository {
    fun getUserDataDtoById(id: Long): UserDataDto
}

@Repository
class QUserRepositoryImpl(private val queryFactory: JPAQueryFactory) : QUserRepository {

    override fun getUserDataDtoById(id: Long): UserDataDto {
        return queryFactory.from(user)
            .innerJoin(userAccount).on(userAccount.user.id.eq(user.id).and(userAccount.isEnabled.isTrue).and(userAccount.isDeleted.isFalse))
            .innerJoin(avatar).on(avatar.user.id.eq(user.id).and(avatar.isMain.isTrue))
            .innerJoin(avatarUserItem).on(avatarUserItem.avatar.id.eq(avatar.id))
            .innerJoin(avatarUserItem.userItem, userItem)
            .innerJoin(userItem.item, item)
            .innerJoin(item.itemType, itemType)
            .innerJoin(userPoint).on(userPoint.user.id.eq(user.id))
            .where(user.id.eq(id).and(user.isEnabled.isTrue).and(user.isDeleted.isFalse))
            .transform(groupBy(user.id).list(
                Projections.constructor(
                    UserDataDto::class.java,
                    user.id,
                    user.nickname,
                    user.authorityType,
                    userPoint.point,
                    list(
                        UserAccountDataDto::class.java,
                        userAccount.id,
                        userAccount.email,
                        userAccount.accountType
                    ),
                    list(
                        EquippedItemDto::class.java,
                        item.id,
                        item.itemType.id,
                        item.filePath
                    )
                )
            ))[0]
    }

}