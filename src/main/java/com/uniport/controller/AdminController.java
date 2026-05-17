package com.uniport.controller;

import com.uniport.entity.Competition;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.service.CompetitionService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import com.uniport.service.UserDeletionReferenceCleanupService;
import com.uniport.service.VoteService;
import com.uniport.service.feedback.GenerateGroupInvestmentFeedbackReportUseCase;
import com.uniport.service.importer.AssetMasterImportService;
import com.uniport.service.importer.ImportResult;
import com.uniport.websocket.PriceBroadcaster;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 명세 §10: 관리자 (Admin). role === "admin" 사용자만 접근.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final UserDeletionReferenceCleanupService cleanupService;
    private final MatchingRoomService matchingRoomService;
    private final CompetitionService competitionService;
    private final RankingService rankingService;
    private final ChatService chatService;
    private final VoteService voteService;
    private final PriceBroadcaster priceBroadcaster;
    private final GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase;
    private final AssetMasterImportService assetMasterImportService;

    public AdminController(AuthService authService, UserRepository userRepository,
                           MatchingRoomRepository matchingRoomRepository,
                           MatchingRoomMemberRepository matchingRoomMemberRepository,
                           UserDeletionReferenceCleanupService cleanupService,
                           MatchingRoomService matchingRoomService, CompetitionService competitionService, RankingService rankingService, ChatService chatService, VoteService voteService,
                           PriceBroadcaster priceBroadcaster,
                           GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase,
                           AssetMasterImportService assetMasterImportService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.cleanupService = cleanupService;
        this.matchingRoomService = matchingRoomService;
        this.competitionService = competitionService;
        this.rankingService = rankingService;
        this.chatService = chatService;
        this.voteService = voteService;
        this.priceBroadcaster = priceBroadcaster;
        this.feedbackReportUseCase = feedbackReportUseCase;
        this.assetMasterImportService = assetMasterImportService;
    }

    private User requireAdmin(String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            throw new ApiException("Admin access required", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /** admin 또는 sisu_admin(준관리자) 허용. SISU-admin 페이지용 API. */
    private User requireAdminOrSisuAdmin(String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        if (user == null) {
            throw new ApiException("Admin or SISU-admin access required", HttpStatus.FORBIDDEN);
        }
        String role = user.getRole() != null ? user.getRole() : "";
        if (!"admin".equalsIgnoreCase(role) && !"sisu_admin".equalsIgnoreCase(role)) {
            throw new ApiException("Admin or SISU-admin access required", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /** 실시간 시세 WebSocket(/prices) 세션별 구독 종목. 키=세션ID, 값=구독 중인 종목코드 목록. 관리자 전용. */
    @GetMapping("/price-subscriptions")
    public ResponseEntity<Map<String, List<String>>> getPriceSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Map<String, Set<String>> summary = priceBroadcaster.getSubscriptionSummaryBySessionId();
        Map<String, List<String>> body = new HashMap<>();
        summary.forEach((sessionId, codes) -> body.put(sessionId, new ArrayList<>(codes)));
        return ResponseEntity.ok(body);
    }

    /** 전체 종목 검색용 국내/미국 종목 마스터를 수동 갱신. 관리자 전용. */
    @PostMapping("/assets/import/all")
    public ResponseEntity<Map<String, Object>> importAllAssets(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            AssetMasterImportService.CombinedImportResult result = assetMasterImportService.importAll();
            return ResponseEntity.ok(combinedImportResponse("all", result));
        } catch (Exception e) {
            throw new ApiException("종목 마스터 import에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 국내 KRX 종목 마스터를 수동 갱신. 관리자 전용. */
    @PostMapping("/assets/import/domestic")
    public ResponseEntity<Map<String, Object>> importDomesticAssets(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            ImportResult result = assetMasterImportService.importDomestic();
            return ResponseEntity.ok(singleImportResponse("domestic", result));
        } catch (Exception e) {
            throw new ApiException("국내 종목 마스터 import에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 미국 종목 마스터를 수동 갱신. 관리자 전용. */
    @PostMapping("/assets/import/us")
    public ResponseEntity<Map<String, Object>> importUsAssets(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        ImportResult result = assetMasterImportService.importUs();
        return ResponseEntity.ok(singleImportResponse("us", result));
    }

    /** §10-1: 대회 목록 (관리자용). DB에 저장된 대회 반환. SISU-admin도 팀 순위 드롭다운용으로 조회 가능. */
    @GetMapping("/competitions")
    public ResponseEntity<List<Map<String, Object>>> getCompetitions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdminOrSisuAdmin(authorization);
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

    /** §10-4: 대회별 팀 목록 (관리자용). DB 팀 랭킹 기준. members 포함. SISU-admin도 팀 순위 탭용으로 조회 가능. */
    @GetMapping("/competitions/{competitionId}/teams")
    public ResponseEntity<List<Map<String, Object>>> getCompetitionTeams(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long competitionId) {
        requireAdminOrSisuAdmin(authorization);
        List<Map<String, Object>> list = rankingService.getCompetingTeams(competitionId, null);
        for (Map<String, Object> m : list) {
            Object teamIdObj = m.get("teamId");
            if (teamIdObj instanceof String) {
                String s = (String) teamIdObj;
                Long roomId = null;
                if (s.startsWith("team-")) {
                    try {
                        roomId = Long.parseLong(s.substring(5));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (roomId != null) {
                    List<MatchingRoomMember> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(roomId);
                    List<Map<String, Object>> memberList = members.stream()
                            .map(mem -> {
                                User u = mem.getUser();
                                Map<String, Object> mm = new HashMap<>();
                                mm.put("userId", u != null ? u.getId() : null);
                                mm.put("nickname", u != null ? (u.getNickname() != null ? u.getNickname() : "") : "");
                                return mm;
                            })
                            .collect(Collectors.toList());
                    m.put("members", memberList);
                }
            }
        }
        return ResponseEntity.ok(list);
    }

    /** §10-5: 매칭방 목록 (관리자용). SISU-admin도 팀 관리 탭용으로 조회 가능. */
    @GetMapping("/matching-rooms")
    public ResponseEntity<List<Map<String, Object>>> getMatchingRooms(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdminOrSisuAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.list(null));
    }

    /** 팀(매칭방) 삭제. admin/SISU-admin 전용. roomId: "room-1" 또는 "1" */
    @DeleteMapping("/matching-rooms/{roomId}")
    public ResponseEntity<Map<String, Object>> deleteMatchingRoom(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        requireAdminOrSisuAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.deleteRoomByAdmin(roomId));
    }

    /** 팀(매칭방)에서 멤버 강제 제거. admin/SISU-admin 전용. roomId: "room-1" 또는 "1" */
    @DeleteMapping("/matching-rooms/{roomId}/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId,
            @PathVariable Long userId) {
        requireAdminOrSisuAdmin(authorization);
        return ResponseEntity.ok(matchingRoomService.removeMemberByAdmin(roomId, userId));
    }

    /** 팀(방)별 거래내역 로그: 해당 방의 투표(Vote) + 바로 체결(Order) 합산, 일시 역순. admin/SISU-admin. roomId: "room-1" 또는 "1" */
    @GetMapping("/matching-rooms/{roomId}/votes")
    public ResponseEntity<List<Map<String, Object>>> getRoomVotes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        requireAdminOrSisuAdmin(authorization);
        Long groupId = parseRoomIdToGroupId(roomId);
        return ResponseEntity.ok(voteService.getVotesAndOrdersByRoomId(groupId));
    }

    /** 팀(방)별 채팅 로그. admin/SISU-admin 전용. roomId: "room-1" 또는 "1" */
    @GetMapping("/matching-rooms/{roomId}/chat-messages")
    public ResponseEntity<List<Map<String, Object>>> getRoomChatMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        requireAdminOrSisuAdmin(authorization);
        Long groupId = parseRoomIdToGroupId(roomId);
        return ResponseEntity.ok(chatService.getMessages(groupId));
    }

    /** 그룹 모의투자 종료 후 피드백 리포트 생성/재조회. room status를 ended로 고정해 스케줄러와 동일한 기준을 만든다. */
    @PostMapping("/matching-rooms/{roomId}/feedback-report")
    public ResponseEntity<Map<String, Object>> generateFeedbackReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        return endRoomAndGenerateFeedbackReport(authorization, roomId, false);
    }

    /** 운영 관리자용 강제 종료. QA/운영 상황에서 즉시 종료 시각으로 리포트를 생성한다. */
    @PostMapping("/matching-rooms/{roomId}/force-end-feedback-report")
    public ResponseEntity<Map<String, Object>> forceEndFeedbackReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        return endRoomAndGenerateFeedbackReport(authorization, roomId, true);
    }

    private ResponseEntity<Map<String, Object>> endRoomAndGenerateFeedbackReport(String authorization,
                                                                                String roomId,
                                                                                boolean overwriteEndedAt) {
        requireAdminOrSisuAdmin(authorization);
        Long groupId = parseRoomIdToGroupId(roomId);
        var room = matchingRoomRepository.findById(groupId)
                .orElseGet(() -> restoreMissingRoomForForceEnd(groupId));
        if (!"ended".equalsIgnoreCase(room.getStatus())) {
            room.setStatus("ended");
        }
        if (overwriteEndedAt || room.getEndedAt() == null) {
            room.setEndedAt(java.time.Instant.now());
        }
        matchingRoomRepository.save(room);
        return ResponseEntity.ok(feedbackReportUseCase.generateForRoom(groupId));
    }

    private MatchingRoom restoreMissingRoomForForceEnd(Long groupId) {
        List<User> teamUsers = userRepository.findByTeamId("team-" + groupId);
        if (teamUsers == null || teamUsers.isEmpty()) {
            throw new ApiException("방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        var now = java.time.Instant.now();
        matchingRoomRepository.insertRecoveredRoom(
                groupId,
                "복구된 매칭방 room-" + groupId,
                Math.max(3, teamUsers.size()),
                teamUsers.size(),
                "ended",
                "PUBLIC",
                now,
                now
        );
        MatchingRoom saved = matchingRoomRepository.findById(groupId)
                .orElseThrow(() -> new ApiException("방 복구에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR));
        for (User teamUser : teamUsers) {
            if (teamUser.getId() != null
                    && !matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(groupId, teamUser.getId())) {
                matchingRoomMemberRepository.save(MatchingRoomMember.of(saved, teamUser));
            }
        }
        return saved;
    }

    private static Long parseRoomIdToGroupId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ApiException("방 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        String s = roomId.trim();
        if (s.startsWith("room-")) {
            s = s.substring(5);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /** 팀별 피드백 전송: 입력한 방들에 피드백 메시지 브로드캐스트 및 해당 방 채팅 비활성화 */
    @PostMapping("/chat/feedback")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> sendFeedbackToRooms(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        requireAdmin(authorization);
        Object deliveriesObj = body != null ? body.get("deliveries") : null;
        List<Map<String, Object>> deliveries = deliveriesObj instanceof List ? (List<Map<String, Object>>) deliveriesObj : null;
        if (deliveries == null || deliveries.isEmpty()) {
            throw new ApiException("deliveries is required and must be non-empty", HttpStatus.BAD_REQUEST);
        }
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> d : deliveries) {
            Object roomIdObj = d.get("roomId");
            String content = d.containsKey("content") ? String.valueOf(d.get("content")) : "";
            Long roomId = null;
            if (roomIdObj instanceof Number) {
                roomId = ((Number) roomIdObj).longValue();
            } else if (roomIdObj instanceof String) {
                String s = (String) roomIdObj;
                if (s.startsWith("room-")) s = s.substring(5);
                try {
                    roomId = Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (roomId == null || roomId < 1) {
                errors.add("Invalid roomId: " + roomIdObj);
                continue;
            }
            try {
                chatService.saveFeedbackMessage(roomId, content);
            } catch (Exception e) {
                errors.add("room " + roomId + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("success", false, "message", String.join("; ", errors)));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Feedback sent to selected rooms."));
    }

    /** §10-6: 유저 목록 (관리자용). SISU-admin도 유저 관리 탭용으로 조회 가능. */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdminOrSisuAdmin(authorization);
        List<Map<String, Object>> list = userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId() != null ? String.valueOf(u.getId()) : null);
                    m.put("studentId", u.getStudentId());
                    m.put("nickname", u.getNickname());
                    m.put("teamId", u.getTeamId());
                    m.put("role", u.getRole() != null ? u.getRole() : "user");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** 유저 삭제 (admin/SISU-admin). 본인·전체관리자(admin) 계정은 삭제 불가. FK 제약으로 주문·보유·매칭방멤버를 먼저 삭제. */
    @Transactional
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId) {
        User admin = requireAdminOrSisuAdmin(authorization);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        if (target.getId().equals(admin.getId())) {
            throw new ApiException("본인 계정은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if ("admin".equalsIgnoreCase(target.getRole())) {
            throw new ApiException("관리자 계정은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        cleanupService.cleanupUserReferences(userId);
        userRepository.deleteById(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted"));
    }

    private static Map<String, Object> combinedImportResponse(
            String scope,
            AssetMasterImportService.CombinedImportResult result) {
        return mapOf(
                "success", true,
                "scope", scope,
                "domestic", importResultMap(result.domestic()),
                "us", importResultMap(result.us()),
                "total", importResultMap(result.total())
        );
    }

    private static Map<String, Object> singleImportResponse(String scope, ImportResult result) {
        return mapOf(
                "success", true,
                "scope", scope,
                "result", importResultMap(result),
                "total", importResultMap(result)
        );
    }

    private static Map<String, Object> importResultMap(ImportResult result) {
        return mapOf(
                "inserted", result.getInserted(),
                "updated", result.getUpdated(),
                "skipped", result.getSkipped()
        );
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
