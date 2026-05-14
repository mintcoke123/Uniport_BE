package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.PushTokenRegisterRequestDTO;
import com.uniport.dto.PushTokenResponseDTO;
import com.uniport.dto.PushTokenUnregisterRequestDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/push-tokens")
@Tag(name = "Push Tokens", description = "FCM/APNs device token registration API")
public class PushTokenController {

    private final PushTokenService pushTokenService;
    private final CurrentUserResolver currentUserResolver;

    public PushTokenController(PushTokenService pushTokenService,
                               CurrentUserResolver currentUserResolver) {
        this.pushTokenService = pushTokenService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    @Operation(summary = "디바이스 푸시 토큰 등록/갱신", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<PushTokenResponseDTO> registerToken(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PushTokenRegisterRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pushTokenService.registerToken(user, request));
    }

    @PostMapping("/unregister")
    @Operation(summary = "디바이스 푸시 토큰 해제", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<Void> unregisterToken(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PushTokenUnregisterRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        pushTokenService.unregisterToken(user, request);
        return ResponseEntity.noContent().build();
    }
}
