package com.uniport.controller;

import com.uniport.entity.Competition;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import com.uniport.service.AuthService;
import com.uniport.service.CompetitionService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 명세 §10: 관리자 (Admin). role === "admin" 사용자만 접근.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final MatchingRoomService matchingRoomService;
    private final CompetitionService competitionService;
    private final RankingService rankingService;

    public AdminController(AuthService authService, UserRepository userRepository, MatchingRoomService matchingRoomService, CompetitionService competitionService, RankingService rankingService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.matchingRoomService = matchingRoomService;
        this.competitionService = competitionService;
        this.rankingService = rankingService;
    }

    private User requireAdmin(String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            throw new ApiException("Admin access required", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /** §10-1: 대회 목록 (관리자용). DB에 저장된 대회 반환. */
    @GetMapping("/competitions")
    public ResponseEntity<List<Map<String, Object>>> getCompetitions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        List<Map<String, Object>> list = competitionService.findAll().stream()
                .map(competitionService::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** §10-2: 대회 생성 */
    @PostMapping("/competitions")
    public ResponseEntity<Map<String, Object>> createCompetition(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        requireAdmin(authorization);
        String name = body != null && body.containsKey("name") ? body.get("name") : "새 대회";
        String startDate = body != null && body.containsKey("startDate") ? body.get("startDate") : "2025-03-01T00:00:00";
        String endDate = body != null && body.containsKey("endDate") ? body.get("endDate") : "2025-03-31T23:59:59";
        Competition created = competitionService.create(name, startDate, endDate);
        return ResponseEntity.ok(Map.of("success", true, "message", "Created", "competition", competitionService.toMap(created)));
    }

    /** §10-3: 대회 수정. 어드민에서 끝나는 날짜 등 수정 시 저장됨. */
    @PatchMapping("/competitions/{id}")
    public ResponseEntity<Map<String, Object>> updateCompetition(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        requireAdmin(authorization);
        String name = body != null ? body.get("name") : null;
        String startDate = body != null ? body.get("startDate") : null;
        String endDate = body != null ? body.get("endDate") : null;
        String status = body != null ? body.get("status") : null;
        competitionService.update(id, name, startDate, endDate, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "Updated"));
    }

    /** §10-4: 대회별 팀 목록 (관리자용). DB 팀 랭킹 기준. */
    @GetMapping("/competitions/{competitionId}/teams")
    public ResponseEntity<List<Map<String, Object>>> getCompetitionTeams(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long competitionId) {
        requireAdmin(authorization);
        return ResponseEntity.ok(rankingService.getCompetingTeams(competitionId, null));
    }

    /** §10-5: 매칭방 목록 (관리자용) */
    @GetMapping("/matching-rooms")
    public ResponseEntity<List<Map<String, Object>>> getMatchingRooms(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.list(null));
    }

    /** 팀(매칭방) 삭제. 관리자 전용. roomId: "room-1" 또는 "1" */
    @DeleteMapping("/matching-rooms/{roomId}")
    public ResponseEntity<Map<String, Object>> deleteMatchingRoom(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        requireAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.deleteRoomByAdmin(roomId));
    }

    /** 팀(매칭방)에서 멤버 강제 제거. 관리자 전용. roomId: "room-1" 또는 "1" */
    @DeleteMapping("/matching-rooms/{roomId}/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId,
            @PathVariable Long userId) {
        requireAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.removeMemberByAdmin(roomId, userId));
    }

    /** §10-6: 유저 목록 (관리자용) */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        List<Map<String, Object>> list = userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId() != null ? String.valueOf(u.getId()) : null);
                    m.put("email", u.getEmail());
                    m.put("nickname", u.getNickname());
                    m.put("teamId", u.getTeamId());
                    m.put("role", u.getRole() != null ? u.getRole() : "user");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** 유저 삭제 (관리자 전용). 본인·다른 관리자 계정은 삭제 불가. */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId) {
        User admin = requireAdmin(authorization);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        if (target.getId().equals(admin.getId())) {
            throw new ApiException("본인 계정은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if ("admin".equalsIgnoreCase(target.getRole())) {
            throw new ApiException("관리자 계정은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        userRepository.deleteById(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], kvs[i + 1]);
        }
        return m;
    }
}
