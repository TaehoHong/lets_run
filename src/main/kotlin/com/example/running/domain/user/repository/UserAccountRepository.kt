package com.example.running.domain.user.repository

import com.example.running.domain.user.entity.QUserAccount.Companion.userAccount
import com.example.running.domain.user.entity.UserAccount
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAccountRepository : JpaRepository<UserAccount, Long>, QUserAccountRepository {
    /**
     * 이메일, 계정 타입, 삭제 여부로 조회
     */
    fun findByEmailAndAccountTypeIdAndIsDeletedFalse(email: String, accountTypeId: Short): UserAccount?

    /**
     * 활성화된 계정 존재 여부 확인 (is_deleted=false)
     * 회원탈퇴 후 재가입 시 삭제된 계정과 구분하기 위해 사용
     */
    fun existsByEmailAndIsDeletedFalse(email: String): Boolean
}

interface QUserAccountRepository {
    fun updateIsDeleted(isDeleted: Boolean, id: Long, userId: Long): Boolean
    fun disableAllByUserId(userId: Long): Long
}

@Repository
class UserAccountRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : QUserAccountRepository {

    override fun updateIsDeleted(isDeleted: Boolean, id: Long, userId: Long): Boolean {
        return queryFactory.update(userAccount)
            .set(userAccount.isDeleted, isDeleted)
            .where(
                userAccount.id.eq(id),
                userAccount.user.id.eq(userId)
            ).execute() > 0
    }

    override fun disableAllByUserId(userId: Long): Long {
        return queryFactory.update(userAccount)
            .set(userAccount.isEnabled, false)
            .set(userAccount.isDeleted, true)
            .where(userAccount.user.id.eq(userId))
            .execute()
    }
}