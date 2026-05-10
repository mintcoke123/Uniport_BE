package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.MatchingRoomCreateRequestDTO;
import com.uniport.dto.MatchingRoomInviteUsersRequestDTO;
import com.uniport.dto.MatchingRoomJoinByCodeRequestDTO;
import com.uniport.dto.QuickMatchRequestDTO;
import com.uniport.dto.QuickMatchResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MatchingRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matching-rooms")
@Tag(name = "Matching Rooms", description = "Matching room creation, join, invite, share, and quick match APIs")
public class MatchingRoomController {

    private final MatchingRoomService matchingRoomService;
    private final CurrentUserResolver currentUserResolver;

    public MatchingRoomController(MatchingRoomService matchingRoomService,
                                  CurrentUserResolver currentUserResolver) {
        this.matchingRoomService = matchingRoomService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "List matching rooms", description = "Returns matching room list. If authenticated, each item may include isJoined.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching room list fetched",
                    content = @Content(array = @ArraySchema(schema = @Schema(type = "object"))))
    })
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

    @GetMapping("/{roomId}")
    @Operation(summary = "Get matching room detail", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching room detail fetched",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid room ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> getDetail(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.getRoomDetail(roomId, user));
    }

    @PostMapping
    @Operation(summary = "Create matching room", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching room created",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or user already joined another room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestBody(required = false) MatchingRoomCreateRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String name = body != null && body.getName() != null ? body.getName() : "Matching Room";
        String visibility = body != null ? body.getVisibility() : null;
        String matchType = body != null ? body.getMatchType() : null;
        String marketType = body != null ? body.getMarketType() : null;
        List<Long> inviteeUserIds = body != null && body.getInviteeUserIds() != null ? body.getInviteeUserIds() : List.of();
        Integer capacity = body != null ? body.getCapacity() : null;

        User creator = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.create(name, visibility, capacity, matchType, marketType, inviteeUserIds, creator));
    }

    @PostMapping("/join-by-code")
    @Operation(summary = "Join room by invite code", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Joined room by invite code",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or already joined another room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Invite code not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> joinByCode(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestBody MatchingRoomJoinByCodeRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String inviteCode = body != null ? body.getInviteCode() : null;
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.joinByCode(inviteCode, user));
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "Join public room", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Joined public room",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request, room full, or already joined another room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Private room requires invite code",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> join(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.join(roomId, user));
    }

    @PostMapping("/{roomId}/invitees")
    @Operation(summary = "Invite users to room", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitee list updated",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or room is not friend mode",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Only room members can invite users",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> inviteUsers(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestBody(required = false) MatchingRoomInviteUsersRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        List<Long> inviteeUserIds = body != null && body.getInviteeUserIds() != null ? body.getInviteeUserIds() : List.of();
        return ResponseEntity.ok(matchingRoomService.inviteUsers(roomId, inviteeUserIds, user));
    }

    @GetMapping("/{roomId}/share-payload")
    @Operation(summary = "Get room share payload", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Share payload fetched",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid room ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Only room members can access share payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> getSharePayload(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.getSharePayload(roomId, user));
    }

    @PostMapping("/{roomId}/leave")
    @Operation(summary = "Leave room", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Left room",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or user is not in the room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> leave(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.leave(roomId, user));
    }

    @PostMapping("/{roomId}/start")
    @Operation(summary = "Start room", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room started",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or not enough members",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Only room members can start the room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Map<String, Object>> start(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(matchingRoomService.start(roomId, user));
    }

    @PostMapping("/quick-match")
    @Operation(summary = "Quick match", description = "Starts random, friend, or solo matching quickly.", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Quick match processed",
                    content = @Content(schema = @Schema(implementation = QuickMatchResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or already joined another room",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    public ResponseEntity<Map<String, Object>> quickMatch(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestBody(required = false) QuickMatchRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        String mode = body != null && body.getMode() != null ? body.getMode() : "RANDOM";
        String marketType = body != null && body.getMarketType() != null ? body.getMarketType() : "KR";
        List<Long> inviteeUserIds = body != null && body.getInviteeUserIds() != null
                ? body.getInviteeUserIds()
                : List.of();
        return ResponseEntity.ok(matchingRoomService.quickMatch(mode, marketType, inviteeUserIds, user));
    }
}
