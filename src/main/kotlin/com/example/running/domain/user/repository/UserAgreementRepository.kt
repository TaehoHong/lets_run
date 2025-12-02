package com.example.running.domain.user.repository

import com.example.running.domain.term.entity.QTerm.Companion.term
import com.example.running.domain.user.entity.QUserAgreement.Companion.userAgreement
import com.example.running.domain.user.entity.UserAgreement
import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface UserAgreementRepository: JpaRepository<UserAgreement, Long>, QUserAgreementRepository {

}

interface QUserAgreementRepository {
    fun findTermIdToUserAgreementMapByUserId(userId: Long): Map<Int, UserAgreement>
    fun findAllIsAgreedByUserIdAndIsRequired(userId: Long, isRequired: Boolean): List<Boolean>
}

@Repository
class QUserAgreementRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
): QUserAgreementRepository {

    override fun findAllIsAgreedByUserIdAndIsRequired(userId: Long, isRequired: Boolean): List<Boolean> {
        return queryFactory.select(userAgreement.isAgreed)
            .from(userAgreement)
            .innerJoin(userAgreement.term, term)
            .where(
                userAgreement.user.id.eq(userId),
                term.isRequired.eq(isRequired)
            ).fetch()
    }

    override fun findTermIdToUserAgreementMapByUserId(userId: Long): Map<Int, UserAgreement> {
        return queryFactory.from(userAgreement)
            .innerJoin(userAgreement.term, term)
            .where(
                userAgreement.user.id.eq(userId),
                term.isEnabled.isTrue
            ).transform(
                groupBy(term.id).`as`(userAgreement)
            )
    }
}