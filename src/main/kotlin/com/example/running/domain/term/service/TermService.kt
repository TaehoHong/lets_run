package com.example.running.domain.term.service

import com.example.running.domain.term.entity.Term
import com.example.running.domain.term.repository.TermRepository
import org.springframework.stereotype.Service

@Service
class TermService(
    private val termRepository: TermRepository
) {

    fun getAllTermDto(): List<TermDto> {
        return termRepository.findAll().map { TermDto(it) }
    }

    fun getAll(isEnabled: Boolean): List<Term> {
        return termRepository.findAllByIsEnabled(isEnabled)
    }
}