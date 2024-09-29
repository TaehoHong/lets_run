package com.example.running.domain.running.controller

import com.example.running.domain.common.dto.PageResult
import com.example.running.domain.running.controller.dto.RunningRecordSearch
import com.example.running.domain.running.controller.dto.StartRunningResponse
import com.example.running.domain.running.service.RunningRecordService
import com.example.running.utils.JwtPayloadParser
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RequestMapping("/api/v1/running")
@RestController
class RunningRecordController(
    private val runningRecordService: RunningRecordService
){


    @PostMapping
    fun startRecord(): StartRunningResponse {
        return runningRecordService.startRecord(JwtPayloadParser.getUserId())
            .let { StartRunningResponse(it.id) }
    }

    @GetMapping
    fun search(pageable: Pageable): PageResult<RunningRecordSearch.Response> {
        return runningRecordService.getDtoPage(JwtPayloadParser.getUserId(), pageable)
            .let {
                PageResult.of(it) { record -> RunningRecordSearch.Response(record) }
            }
    }
}