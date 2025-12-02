package com.example.running.domain.term.controller

import com.example.running.domain.term.controller.dto.TermResponse
import com.example.running.domain.term.controller.dto.TermResponses
import com.example.running.domain.term.service.TermService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/terms")
@RestController
class TermController(
    private val termService: TermService
) {

    @GetMapping
    fun findAll(): TermResponses {
        return termService.getAllTermDto().map {
            TermResponse(
                id = it.id,
                type = it.type,
                link = it.link,
                isRequired = it.isRequired
            )
        }.let {
            TermResponses(it)
        }
    }
}