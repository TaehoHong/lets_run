package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningBatchResponse
import com.example.running.domain.running.service.RunningImportService
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/running")
@RestController
class RunningImportController(
    private val runningImportService: RunningImportService
) {

    @PostMapping("/import-batch")
    fun importBatch(@RequestBody request: ImportRunningBatchRequest): ImportRunningBatchResponse {
        return authenticateWithUser { userId ->
            runningImportService.importBatch(userId, request)
        }
    }
}
