package com.example.running.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * 스케줄러 설정
 * - 스레드 풀 크기 설정으로 동시 실행 가능한 스케줄 작업 수 증가
 */
@Configuration
class SchedulingConfig : SchedulingConfigurer {

    companion object {
        private const val POOL_SIZE = 5
        private const val THREAD_NAME_PREFIX = "scheduled-task-"
    }

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = POOL_SIZE
            setThreadNamePrefix(THREAD_NAME_PREFIX)
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
        }
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler())
    }
}
