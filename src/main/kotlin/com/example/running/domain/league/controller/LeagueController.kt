package com.example.running.domain.league.controller

import com.example.running.domain.league.controller.dto.CurrentLeagueResponse
import com.example.running.domain.league.controller.dto.LeagueHistoryResponse
import com.example.running.domain.league.controller.dto.LeagueProfileResponse
import com.example.running.domain.league.controller.dto.LeagueResultResponse
import com.example.running.domain.league.service.LeagueService
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/league")
@RestController
class LeagueController(
    private val leagueService: LeagueService
) {

    /**
     * 현재 리그 정보 조회
     */
    @GetMapping("/current")
    fun getCurrentLeague(): CurrentLeagueResponse? {
        return authenticateWithUser { userId ->
            leagueService.getCurrentLeague(userId)?.let { CurrentLeagueResponse.from(it) }
        }
    }

    /**
     * 리그 프로필 조회
     */
    @GetMapping("/profile")
    fun getLeagueProfile(): LeagueProfileResponse? {
        return authenticateWithUser { userId ->
            leagueService.getLeagueProfile(userId)?.let { LeagueProfileResponse.from(it) }
        }
    }

    /**
     * 리그 참가
     */
    @PostMapping("/join")
    fun joinLeague() {
        authenticateWithUser { userId ->
            leagueService.joinLeague(userId)
        }
    }

    /**
     * 미확인 리그 결과 조회
     */
    @GetMapping("/result")
    fun getUncheckedResult(): LeagueResultResponse? {
        return authenticateWithUser { userId ->
            leagueService.getUncheckedResult(userId)?.let { LeagueResultResponse.from(it) }
        }
    }

    /**
     * 리그 결과 확인 처리
     */
    @PostMapping("/result/confirm")
    fun confirmResult() {
        authenticateWithUser { userId ->
            leagueService.confirmResult(userId)
        }
    }

    /**
     * 리그 히스토리 조회
     */
    @GetMapping("/history")
    fun getHistory(
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "10") size: Int
    ): LeagueHistoryResponse {
        return authenticateWithUser { userId ->
            val (histories, participantIds) = leagueService.getHistory(userId, cursor, size)
            LeagueHistoryResponse.from(histories, participantIds, size)
        }
    }
}
