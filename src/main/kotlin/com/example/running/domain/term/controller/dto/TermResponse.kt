package com.example.running.domain.term.controller.dto

import com.example.running.domain.term.entity.TermType

class TermResponses (
    val terms: List<TermResponse>
)

class TermResponse (
    val id: Int,
    val link: String,
    val type: TermType,
    val isRequired: Boolean
)