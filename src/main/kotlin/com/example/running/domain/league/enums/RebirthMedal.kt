package com.example.running.domain.league.enums

enum class RebirthMedal(val minCount: Int) {
    NONE(0),
    BRONZE(1),
    SILVER(2),
    GOLD(3),
    PLATINUM(4),
    DIAMOND(5);

    companion object {
        fun fromRebirthCount(count: Int): RebirthMedal {
            return when {
                count >= 5 -> DIAMOND
                count == 4 -> PLATINUM
                count == 3 -> GOLD
                count == 2 -> SILVER
                count == 1 -> BRONZE
                else -> NONE
            }
        }
    }
}
