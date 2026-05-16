package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.service.ChatRoomService;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MatchingRoomService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/rooms")
@Tag(name = "Chat Rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final CurrentUserResolver currentUserResolver;
    private final MatchingRoomService matchingRoomService;

    public ChatRoomController(ChatRoomService chatRoomService,
                              CurrentUserResolver currentUserResolver,
                              MatchingRoomService matchingRoomService) {
        this.chatRoomService = chatRoomService;
        this.currentUserResolver = currentUserResolver;
        this.matchingRoomService = matchingRoomService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMyChatRooms(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(chatRoomService.getMyChatRooms(user));
    }

    @GetMapping("/{roomId}/summary")
    public ResponseEntity<Map<String, Object>> getChatRoomSummary(
            @PathVariable Long roomId,
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        try {
            return ResponseEntity.ok(chatRoomService.getChatRoomSummary(roomId, user));
        } catch (IllegalArgumentException ex) {
            HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("access denied")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.NOT_FOUND;
            throw new ApiException(ex.getMessage(), status);
        }
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Map<String, Object>> markChatRoomAsRead(
            @PathVariable Long roomId,
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        try {
            return ResponseEntity.ok(chatRoomService.markRoomAsRead(roomId, user));
        } catch (IllegalArgumentException ex) {
            HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("access denied")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.NOT_FOUND;
            throw new ApiException(ex.getMessage(), status);
        }
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Map<String, Object>> leaveChatRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.leave("room-" + roomId, user));
    }
}
