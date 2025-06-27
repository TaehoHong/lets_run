package com.example.running.domain.shoe.repository

import com.example.running.domain.shoe.entity.Shoe
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ShoeRepository: JpaRepository<Shoe, Long>, QShoeRepository


interface QShoeRepository {

}


@Repository
class QShoeRepositoryImpl(private val queryFactory: JPAQueryFactory): QShoeRepository {

}