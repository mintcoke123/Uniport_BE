package com.uniport.controller;

import com.uniport.dto.BetaIosTestFlightSyncResponseDTO;
import com.uniport.service.BetaIosApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/beta/ios")
public class BetaIosTestFlightSyncAdminController {

    private static final String TOKEN_HEADER = "X-Beta-Admin-Token";

    private final BetaIosApplicationService betaIosApplicationService;
    private final String adminToken;

    public BetaIosTestFlightSyncAdminController(
            BetaIosApplicationService betaIosApplicationService,
            @Value("${app.beta.admin-token:}") String adminToken
    ) {
        this.betaIosApplicationService = betaIosApplicationService;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    @PostMapping("/testflight-sync")
    public ResponseEntity<?> syncTestFlightGroup(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        if (adminToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Beta admin token is not configured."));
        }
        if (!matchesToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid beta admin token."));
        }

        BetaIosTestFlightSyncResponseDTO response = betaIosApplicationService.syncPendingInternalTesters();
        return ResponseEntity.ok(response);
    }

    private boolean matchesToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
