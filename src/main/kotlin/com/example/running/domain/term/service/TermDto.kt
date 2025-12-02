package com.example.running.domain.term.service

import com.example.running.domain.term.entity.Term
import com.example.running.domain.term.entity.TermType

class TermDto(
    val id: Int,
    val type: TermType,
    val link: String,
    val version: String,
    val isRequired: Boolean,
    val isEnabled: Boolean,
) {
    constructor(term: Term): this(
        id = term.id,
        type = term.type,
        link = term.link,
        version = term.version,
        isRequired = term.isRequired,
        isEnabled = term.isEnabled
    )
}