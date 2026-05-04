package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.FriendListResponseDTO;
import com.uniport.dto.FriendRequestCreateDTO;
import com.uniport.dto.FriendRequestDecisionDTO;
import com.uniport.dto.FriendRequestListResponseDTO;
import com.uniport.dto.FriendRequestResponseDTO;
import com.uniport.dto.FriendsDashboardResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PointSocialDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friends", description = "친구 API")
public class FriendsController {

    private final PointSocialDataService pointSocialDataService;
    private final CurrentUserResolver currentUserResolver;

    public FriendsController(PointSocialDataService pointSocialDataService,
                             CurrentUserResolver currentUserResolver) {
        this.pointSocialDataService = pointSocialDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "내 친구 목록 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendListResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendListResponseDTO> getFriends(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(example = "고")
            @RequestParam(value = "keyword", required = false) String keyword) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getFriends(user, keyword));
    }

    @PostMapping("/requests")
    @Operation(summary = "친구 요청", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "친구 요청 성공",
                    content = @Content(schema = @Schema(implementation = FriendRequestResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 사용자 ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "이미 친구이거나 이미 요청한 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendRequestResponseDTO> requestFriend(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody FriendRequestCreateDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(pointSocialDataService.requestFriend(user, request));
    }

    @PatchMapping("/requests/{requestId}")
    @Operation(summary = "친구 요청 수락 또는 거절", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "친구 요청 상태 변경 성공",
                    content = @Content(schema = @Schema(implementation = FriendRequestResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "친구 요청 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendRequestResponseDTO> decideFriendRequest(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String requestId,
            @RequestBody FriendRequestDecisionDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.decideFriendRequest(user, requestId, request));
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "내가 요청한 친구 목록 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendRequestListResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendRequestListResponseDTO> getSentRequests(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getSentFriendRequests(user));
    }

    @GetMapping("/requests/received")
    @Operation(summary = "내가 신청한 친구 목록 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendRequestListResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendRequestListResponseDTO> getReceivedRequests(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getReceivedFriendRequests(user));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "친구 대시보드 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendsDashboardResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<FriendsDashboardResponseDTO> getFriendsDashboard(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getFriendsDashboard(user));
    }
}
