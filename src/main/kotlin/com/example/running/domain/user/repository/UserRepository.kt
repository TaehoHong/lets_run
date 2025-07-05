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
import com.example.running.domain.user.entity.QAccountType.Companion.accountType
import com.example.running.domain.user.entity.QUser.Companion.user
import com.example.running.domain.user.entity.QUserAccount.Companion.userAccount
import com.example.running.domain.user.entity.User
import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.core.group.GroupBy.list
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
            .innerJoin(userAccount.accountType, accountType)
            .innerJoin(avatar).on(avatar.user.id.eq(user.id).and(avatar.isMain.isTrue))
            .leftJoin(avatarUserItem).on(avatarUserItem.avatar.id.eq(avatar.id))
            .leftJoin(avatarUserItem.userItem, userItem)
            .leftJoin(userItem.item, item)
            .leftJoin(item.itemType, itemType)
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
                        Projections.constructor(
                                UserAccountDataDto::class.java,
                                userAccount.id,
                                userAccount.email,
                                accountType.name
                        )
                    ),
                    list(
                            Projections.constructor(
                                EquippedItemDto::class.java,
                                item.id,
                                item.itemType.id,
                                item.name,
                                item.filePath
                        ).skipNulls()
                    ),
                )
            ))[0]
    }

}