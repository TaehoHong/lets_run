package com.example.running.domain.league.enums

/**
 * 봇 유형
 *
 * @see <a href="https://www.notion.so/2cc405e9dd388175bf1cf008612a3876">리그 기획서</a>
 */
enum class BotType(
    val description: String,
    val distributionRatio: Double
) {
    /**
     * 페이스메이커 - 목표 기준선 제공
     * - 거리 범위: 티어 승격 컷 ±5%
     * - 투입 비율: 30%
     */
    PACER("페이스메이커", 0.3),

    /**
     * 경쟁 상대 - 실제 경쟁 유도
     * - 거리 범위: 티어 평균의 60~120%
     * - 투입 비율: 70%
     */
    COMPETITOR("경쟁 상대", 0.7);

    companion object {
        /**
         * 봇 수에 따른 PACER/COMPETITOR 분배 계산
         *
         * @param totalBots 총 필요 봇 수
         * @return Pair(PACER 수, COMPETITOR 수)
         */
        fun calculateDistribution(totalBots: Int): Pair<Int, Int> {
            val pacerCount = kotlin.math.ceil(totalBots * PACER.distributionRatio).toInt()
            val competitorCount = totalBots - pacerCount
            return Pair(pacerCount, competitorCount)
        }
    }
}
