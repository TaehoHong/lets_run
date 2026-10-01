package com.example.running.domain.league.controller

import com.example.running.domain.league.controller.dto.UpdateDistanceRequest
import com.example.running.domain.league.service.LeagueParticipantService
import com.example.running.domain.league.service.LeagueService
import com.example.running.helper.authenticateWithUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RequestMapping("/api/v1/league-participants")
@RestController
class LeagueParticipantController(
    private val leagueParticipantService: LeagueParticipantService,
    private val leagueService: LeagueService
) {

    /**
     * 참가자 거리 업데이트
     * 본인의 participant만 업데이트 가능
     */
    @PatchMapping("/{participantId}/distance")
    fun updateDistance(
        @PathVariable participantId: Long,
        @RequestBody request: UpdateDistanceRequest
    ) {
        authenticateWithUser { userId ->
            // 본인의 participant인지 확인
            val currentParticipant = leagueParticipantService.getCurrentParticipant(userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "현재 참가 중인 리그가 없습니다")

            if (currentParticipant.id != participantId) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 참가 정보만 수정할 수 있습니다")
            }

            // 기존 앱의 distance 요청 형식은 유지하되, 저장된 시즌 합계로 중복 가산을 막는다.
            leagueService.raiseCurrentLeagueDistance(userId)
        }
    }
}
