package com.example.running.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthyCheckController {

    @Value("\${server.name}")
    lateinit var serverName: String

    @Value("\${server.type}")
    lateinit var activeType: String

    @GetMapping("healthy-check")
    fun healthyCheck() : String {
        //
        return serverName
    }


    @GetMapping("active-type")
    fun activeType(): String {
        return activeType
    }
}