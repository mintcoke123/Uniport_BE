package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.PushTestRequestDTO;
import com.uniport.dto.PushTestResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PushNotificationService;
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
@RequestMapping("/api/users/me/push-test")
@Tag(name = "Push Test", description = "Authenticated user's push notification test API")
public class PushTestController {

    private final PushNotificationService pushNotificationService;
    private final CurrentUserResolver currentUserResolver;

    public PushTestController(PushNotificationService pushNotificationService,
                              CurrentUserResolver currentUserResolver) {
        this.pushNotificationService = pushNotificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    @Operation(summary = "현재 사용자 기기로 테스트 푸시 발송", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<PushTestResponseDTO> sendTestPush(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PushTestRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pushNotificationService.sendTestPush(user, request));
    }
}
