package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MatchingRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matching-rooms")
public class MatchingRoomController {

    private final MatchingRoomService matchingRoomService;
    private final CurrentUserResolver currentUserResolver;

    public MatchingRoomController(MatchingRoomService matchingRoomService,
                                  CurrentUserResolver currentUserResolver) {
        this.matchingRoomService = matchingRoomService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = null;
        try {
            user = currentUserResolver.resolveNullable(principal, authorization);
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(matchingRoomService.list(user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String name = body != null && body.get("name") != null ? body.get("name").toString() : "새 매칭방";
        String visibility = body != null && body.get("visibility") != null ? body.get("visibility").toString() : null;
        String matchType = body != null && body.get("matchType") != null ? body.get("matchType").toString() : null;
        String marketType = body != null && body.get("marketType") != null ? body.get("marketType").toString() : null;

        List<Long> inviteeUserIds = new ArrayList<>();
        if (body != null && body.get("inviteeUserIds") instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Number number) {
                    inviteeUserIds.add(number.longValue());
                } else if (item != null) {
                    try {
                        inviteeUserIds.add(Long.parseLong(item.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

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

        User creator = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.create(name, visibility, capacity, matchType, marketType, inviteeUserIds, creator));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<Map<String, Object>> joinByCode(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String inviteCode = body != null ? body.get("inviteCode") : null;
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.joinByCode(inviteCode, user));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Map<String, Object>> join(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.join(roomId, user));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.leave(roomId, user));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String roomId) {
        return ResponseEntity.ok(matchingRoomService.start(roomId));
    }
}