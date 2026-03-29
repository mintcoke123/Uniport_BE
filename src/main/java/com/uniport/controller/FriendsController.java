package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.FriendRequestCreateDTO;
import com.uniport.dto.FriendRequestResponseDTO;
import com.uniport.dto.FriendsDashboardResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MyPageMockService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friends", description = "친구 API")
public class FriendsController {

    private final MyPageMockService myPageMockService;
    private final CurrentUserResolver currentUserResolver;

    public FriendsController(MyPageMockService myPageMockService,
                             CurrentUserResolver currentUserResolver) {
        this.myPageMockService = myPageMockService;
        this.currentUserResolver = currentUserResolver;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(myPageMockService.requestFriend(user, request));
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
        return ResponseEntity.ok(myPageMockService.getFriendsDashboard(user));
    }
}
