package com.example.running.domain.term.repository

import com.example.running.domain.term.entity.Term
import org.springframework.data.jpa.repository.JpaRepository

interface TermRepository: JpaRepository<Term, Int> {
    fun findAllByIsEnabled(isEnabled: Boolean): List<Term>
}