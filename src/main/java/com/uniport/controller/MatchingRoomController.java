package com.uniport.controller;

import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.MatchingRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 명세 §9: 매칭방. 참가/나가기는 로그인 사용자 기준으로 하며, 같은 멤버의 중복 참여 불가.
 */
@RestController
@RequestMapping("/api/matching-rooms")
public class MatchingRoomController {

    private final MatchingRoomService matchingRoomService;
    private final AuthService authService;

    public MatchingRoomController(MatchingRoomService matchingRoomService, AuthService authService) {
        this.matchingRoomService = matchingRoomService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = null;
        try {
            if (authorization != null && !authorization.isBlank()) {
                user = authService.getUserFromToken(authorization);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(matchingRoomService.list(user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String name = body != null && body.get("name") != null ? body.get("name").toString() : "새 매칭방";
        String visibility = body != null && body.get("visibility") != null ? body.get("visibility").toString() : null;
        Integer capacity = null;
        if (body != null && body.get("capacity") != null) {
            Object c = body.get("capacity");
            if (c instanceof Number) {
                capacity = ((Number) c).intValue();
            } else {
                try {
                    capacity = Integer.parseInt(c.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        User creator = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        return ResponseEntity.ok(matchingRoomService.create(name, visibility, capacity, creator));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<Map<String, Object>> joinByCode(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String inviteCode = body != null ? body.get("inviteCode") : null;
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        return ResponseEntity.ok(matchingRoomService.joinByCode(inviteCode, user));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        return ResponseEntity.ok(matchingRoomService.join(roomId, user));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        return ResponseEntity.ok(matchingRoomService.leave(roomId, user));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String roomId) {
        return ResponseEntity.ok(matchingRoomService.start(roomId));
    }
}
