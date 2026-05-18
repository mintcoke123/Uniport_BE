package com.uniport.controller;

import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.CompetitionParticipationService;
import com.uniport.service.CompetitionSettlementService;
import com.uniport.service.CompetitionService;
import com.uniport.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final CompetitionParticipationService competitionParticipationService;
    private final CompetitionSettlementService competitionSettlementService;

    public CompetitionController(CompetitionService competitionService,
                                 RankingService rankingService,
                                 AuthService authService,
                                 CompetitionParticipationService competitionParticipationService,
                                 CompetitionSettlementService competitionSettlementService) {
        this.competitionService = competitionService;
        this.rankingService = rankingService;
        this.authService = authService;
        this.competitionParticipationService = competitionParticipationService;
        this.competitionSettlementService = competitionSettlementService;
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

    @GetMapping("/{competitionId}/teams/{teamId}")
    public ResponseEntity<Map<String, Object>> getCompetitionTeamDetail(
            @PathVariable Long competitionId,
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        return ResponseEntity.ok(competitionSettlementService.getTeamDetail(competitionId, teamId, user));
    }

    @GetMapping("/{competitionId}/summary")
    public ResponseEntity<Map<String, Object>> getCompetitionSummary(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var competition = competitionService.findAll().stream()
                .filter(item -> item.getId() != null && item.getId().equals(competitionId))
                .findFirst()
                .orElseThrow(() -> new com.uniport.exception.ApiException("대회를 찾을 수 없습니다.", org.springframework.http.HttpStatus.NOT_FOUND));

        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        List<Map<String, Object>> teams = rankingService.getCompetingTeams(competitionId, user);

        Map<String, Object> body = new HashMap<>();
        body.put("competitionId", competition.getId());
        body.put("name", competition.getName());
        body.put("status", competition.getStatus() != null ? competition.getStatus().toUpperCase() : "UPCOMING");
        body.put("participantTeamCount", teams.size());
        body.put("remainingTime", buildRemainingTime(competition.getEndDate()));
        body.put("summaryCards", List.of(
                Map.of("label", "참가 팀 수", "value", teams.size(), "unit", "팀"),
                Map.of("label", "남은 시간", "value", buildRemainingTimeLabel(competition.getEndDate()), "unit", "")
        ));
        body.put("upcomingCompetitions", competitionService.findByStatus("upcoming").stream()
                .limit(3)
                .map(item -> Map.<String, Object>of(
                        "competitionId", item.getId(),
                        "name", item.getName(),
                        "statusLabel", "참가 신청",
                        "daysRemaining", Math.max(0, competitionService.daysRemaining(item.getEndDate())),
                        "startDate", item.getStartDate(),
                        "endDate", item.getEndDate()
                ))
                .toList());
        String participantTeamId = resolveParticipantTeamId(user);
        body.put("application", competitionParticipationService.getApplicationStatus(competitionId, user, participantTeamId));
        body.put("liveRanking", teams.stream().limit(5).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{competitionId}/apply")
    public ResponseEntity<Map<String, Object>> applyToCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        String participantTeamId = body != null && body.get("teamId") != null ? String.valueOf(body.get("teamId")) : resolveParticipantTeamId(user);
        String participantName = body != null && body.get("teamName") != null ? String.valueOf(body.get("teamName")) : null;
        return ResponseEntity.ok(competitionParticipationService.apply(competitionId, user, participantTeamId, participantName));
    }

    @PostMapping("/{competitionId}/cancel-application")
    public ResponseEntity<Map<String, Object>> cancelCompetitionApplication(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        String participantTeamId = body != null && body.get("teamId") != null ? String.valueOf(body.get("teamId")) : resolveParticipantTeamId(user);
        return ResponseEntity.ok(competitionParticipationService.cancel(competitionId, user, participantTeamId));
    }

    @GetMapping("/{competitionId}/application-status")
    public ResponseEntity<Map<String, Object>> getCompetitionApplicationStatus(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        return ResponseEntity.ok(competitionParticipationService.getApplicationStatus(
                competitionId,
                user,
                resolveParticipantTeamId(user)
        ));
    }

    @GetMapping("/applications/me")
    public ResponseEntity<List<Map<String, Object>>> getMyCompetitionApplications(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        return ResponseEntity.ok(competitionParticipationService.getMyApplications(user));
    }

    @GetMapping("/records/me")
    public ResponseEntity<List<Map<String, Object>>> getMyCompetitionRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        return ResponseEntity.ok(competitionSettlementService.getMyRecords(user));
    }

    @PostMapping("/{competitionId}/settle")
    public ResponseEntity<Map<String, Object>> settleCompetition(@PathVariable Long competitionId) {
        return ResponseEntity.ok(competitionSettlementService.settleCompetition(competitionId));
    }

    private Map<String, Object> buildRemainingTime(String endDate) {
        try {
            LocalDateTime end = LocalDateTime.parse(endDate);
            Duration duration = Duration.between(LocalDateTime.now(), end);
            long totalSeconds = Math.max(0, duration.getSeconds());
            long days = totalSeconds / 86400;
            long hours = (totalSeconds % 86400) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            return Map.of(
                    "days", days,
                    "hours", hours,
                    "minutes", minutes,
                    "seconds", seconds,
                    "label", String.format("%d일 %02d:%02d:%02d", days, hours, minutes, seconds)
            );
        } catch (DateTimeParseException ex) {
            return Map.of("days", 0, "hours", 0, "minutes", 0, "seconds", 0, "label", "0일 00:00:00");
        }
    }

    private String buildRemainingTimeLabel(String endDate) {
        return String.valueOf(buildRemainingTime(endDate).get("label"));
    }

    private String resolveParticipantTeamId(User user) {
        if (user == null) {
            return null;
        }
        if (user.getTeamId() != null && !user.getTeamId().isBlank()) {
            return user.getTeamId();
        }
        return user.getId() != null ? "solo-" + user.getId() : null;
    }
}
