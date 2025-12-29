package com.example.running.domain.league.enums

/**
 * 리그 시즌 상태
 */
enum class LeagueSessionState {
    /**
     * 활성 상태 (월~일 23:59:59)
     * - 실시간 랭킹 갱신
     */
    ACTIVE,

    /**
     * 잠금 상태 (월 00:00~00:15)
     * - 시즌 마감, 데이터 수집 중
     */
    LOCKED,

    /**
     * 정산 중 (월 00:15)
     * - 1차 정산, 결과 발표
     */
    CALCULATING,

    /**
     * 검수 중 (월 00:15~화 00:00)
     * - 지연 업로드 처리, Soft Lock 적용
     */
    AUDITING,

    /**
     * 확정 (화 00:00)
     * - 최종 확정, 히스토리 박제
     */
    FINALIZED;

    fun canAcceptNewRecords(): Boolean = this == ACTIVE

    fun canAcceptLateRecords(): Boolean = this == AUDITING

    fun isSettlementPhase(): Boolean = this in listOf(LOCKED, CALCULATING, AUDITING)

    fun isFinal(): Boolean = this == FINALIZED
}
