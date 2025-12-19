package com.example.running.domain.league.enums

enum class LeagueTierType(val id: Int, val displayOrder: Int) {
    BRONZE(1, 1),
    SILVER(2, 2),
    GOLD(3, 3),
    PLATINUM(4, 4),
    DIAMOND(5, 5),
    CHALLENGER(6, 6);

    companion object {
        fun fromId(id: Int): LeagueTierType {
            return entries.find { it.id == id }
                ?: throw IllegalArgumentException("Unknown tier id: $id")
        }

        fun getNextTier(current: LeagueTierType): LeagueTierType? {
            return entries.find { it.displayOrder == current.displayOrder + 1 }
        }

        fun getPreviousTier(current: LeagueTierType): LeagueTierType? {
            return entries.find { it.displayOrder == current.displayOrder - 1 }
        }

        fun isLowestTier(tier: LeagueTierType): Boolean = tier == BRONZE
        fun isHighestTier(tier: LeagueTierType): Boolean = tier == CHALLENGER
    }
}
