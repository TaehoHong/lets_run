package com.example.running.domain.league.service

import com.example.running.domain.league.enums.LeagueTierType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.random.Random

internal object BotBalancePolicy {
    private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

    private const val PROMOTION_CUT_MULTIPLIER = 1.3
    private const val PACER_VARIANCE_RATE = 0.05
    private const val COMPETITOR_MIN_MULTIPLIER = 0.6
    private const val COMPETITOR_MAX_MULTIPLIER = 1.2

    private val dailyProgressRates = mapOf(
        DayOfWeek.MONDAY to (10..15),
        DayOfWeek.TUESDAY to (25..35),
        DayOfWeek.WEDNESDAY to (40..50),
        DayOfWeek.THURSDAY to (55..65),
        DayOfWeek.FRIDAY to (70..80),
        DayOfWeek.SATURDAY to (85..95),
        DayOfWeek.SUNDAY to (100..100)
    )

    fun targetDistanceForTier(tierType: LeagueTierType): Long {
        return when (tierType) {
            LeagueTierType.BRONZE -> 5_400L
            LeagueTierType.SILVER -> 9_000L
            LeagueTierType.GOLD -> 14_400L
            LeagueTierType.PLATINUM -> 21_600L
            LeagueTierType.DIAMOND -> 28_800L
            LeagueTierType.CHALLENGER -> 36_000L
        }
    }

    fun promotionCutDistance(targetDistance: Long): Long {
        return (targetDistance * PROMOTION_CUT_MULTIPLIER).toLong()
    }

    fun pacerFinalDistance(promotionCutDistance: Long): Long {
        val variance = Random.nextDouble(-PACER_VARIANCE_RATE, PACER_VARIANCE_RATE)
        return (promotionCutDistance * (1 + variance)).toLong()
    }

    fun competitorFinalDistance(targetDistance: Long): Long {
        val multiplier = Random.nextDouble(COMPETITOR_MIN_MULTIPLIER, COMPETITOR_MAX_MULTIPLIER)
        return (targetDistance * multiplier).toLong()
    }

    fun progressRangeForElapsedHours(elapsedHours: Double): IntRange {
        val dayProgress = (elapsedHours / 24.0).coerceIn(0.0, 7.0)
        val dayOfWeek = when {
            dayProgress < 1 -> DayOfWeek.MONDAY
            dayProgress < 2 -> DayOfWeek.TUESDAY
            dayProgress < 3 -> DayOfWeek.WEDNESDAY
            dayProgress < 4 -> DayOfWeek.THURSDAY
            dayProgress < 5 -> DayOfWeek.FRIDAY
            dayProgress < 6 -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }

        return dailyProgressRates.getValue(dayOfWeek)
    }

    fun dailyUpdateWindow(now: OffsetDateTime): DailyUpdateWindow {
        val seoulNow = now.atZoneSameInstant(SEOUL_ZONE)
        val dayOfWeek = seoulNow.dayOfWeek
        val slot = (seoulNow.hour * 2) + (seoulNow.minute / 30)

        return DailyUpdateWindow(
            today = seoulNow.toLocalDate(),
            dayOfWeek = dayOfWeek,
            slot = slot,
            progressRange = dailyProgressRates.getValue(dayOfWeek)
        )
    }

    data class DailyUpdateWindow(
        val today: LocalDate,
        val dayOfWeek: DayOfWeek,
        val slot: Int,
        val progressRange: IntRange
    )
}
