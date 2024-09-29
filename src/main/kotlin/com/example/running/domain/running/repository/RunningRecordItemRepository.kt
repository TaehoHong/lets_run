package com.example.running.domain.running.repository

import com.example.running.domain.running.entity.RunningRecordItem
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface RunningRecordItemRepository: JpaRepository<RunningRecordItem, Long> {
}

@Repository
class RunningRecordItemQueryRepository(
    private val queryFactory: JPAQueryFactory
) {
}