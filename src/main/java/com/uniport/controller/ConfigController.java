package com.uniport.controller;

import com.uniport.service.KisApiService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * 설정/상태 조회. KIS 연동 여부 등.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final KisApiService kisApiService;
    private final Environment env;

    public ConfigController(KisApiService kisApiService, Environment env) {
        this.kisApiService = kisApiService;
        this.env = env;
    }

    /** KIS appkey/appsecret 설정 여부. configured: true면 시세·거래량 등 KIS API 사용 가능. */
    @GetMapping("/kis-status")
    public ResponseEntity<Map<String, Object>> getKisStatus() {
        boolean configured = kisApiService.isKisConfigured();
        return ResponseEntity.ok(Map.of("configured", configured));
    }

    /** KIS 접근토큰 폐기. POST /oauth2/revokeP 호출 후 캐시된 토큰 제거. 다음 API 호출 시 새 토큰 발급. */
    @PostMapping("/kis-revoke")
    public ResponseEntity<Map<String, Object>> revokeKisToken() {
        kisApiService.revokeAccessToken();
        return ResponseEntity.ok(Map.of("success", true, "message", "접근토큰이 폐기되었습니다."));
    }

    /** 실시간(웹소켓) 접속키 발급. local/dev 프로필에서만 200 반환, 그 외 403. */
    @GetMapping("/kis-approval")
    public ResponseEntity<Map<String, Object>> getKisApprovalKey() {
        boolean allowed = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "local".equals(p) || "dev".equals(p));
        if (!allowed) {
            return ResponseEntity.status(403).body(Map.of("message", "Forbidden in non-dev profile"));
        }
        String approvalKey = kisApiService.getWebSocketApprovalKey();
        return ResponseEntity.ok(Map.of("approval_key", approvalKey));
    }
}
