package com.uniport.controller;

import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.CompetitionService;
import com.uniport.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 명세 §4: 대회 (랭킹 방식). 진행 중/예정 대회. 어드민에서 설정한 종료일을 사용.
 */
@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;
    private final RankingService rankingService;
    private final AuthService authService;

    public CompetitionController(CompetitionService competitionService, RankingService rankingService, AuthService authService) {
        this.competitionService = competitionService;
        this.rankingService = rankingService;
        this.authService = authService;
    }

    @GetMapping("/ongoing")
    public ResponseEntity<List<Map<String, Object>>> getOngoing() {
        List<Map<String, Object>> list = new ArrayList<>();
        competitionService.findOngoing().ifPresent(c -> {
            list.add(Map.of(
                    "id", c.getId(),
                    "name", c.getName(),
                    "endDate", c.getEndDate()
            ));
        });
        return ResponseEntity.ok(list);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Map<String, Object>>> getUpcoming() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var c : competitionService.findByStatus("upcoming")) {
            list.add(Map.of(
                    "id", c.getId(),
                    "name", c.getName(),
                    "startDate", c.getStartDate() != null ? c.getStartDate() : ""
            ));
        }
        return ResponseEntity.ok(list);
    }

    /** 대회별 경쟁 팀 목록 (실시간 투자금·수익률·순위). DB 팀 랭킹 기준. */
    @GetMapping("/{competitionId}/teams")
    public ResponseEntity<List<Map<String, Object>>> getCompetingTeams(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        return ResponseEntity.ok(rankingService.getCompetingTeams(competitionId, user));
    }
}
