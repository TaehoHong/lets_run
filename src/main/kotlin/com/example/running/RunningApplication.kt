package com.example.running

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@EnableJpaAuditing
@SpringBootApplication
class RunningApplication

fun main(args: Array<String>) {
    runApplication<RunningApplication>(*args)
}
