package com.uniport.controller;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.exception.ApiErrorCodeResolver;
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

    /** 고정 접근 토큰은 외부에서 교체해야 하므로 애플리케이션에서 폐기할 수 없다. */
    @PostMapping("/kis-revoke")
    public ResponseEntity<Map<String, Object>> revokeKisToken() {
        return ResponseEntity.status(410).body(Map.of(
                "success", false,
                "message", "고정 접근 토큰은 실행 환경에서 교체해야 합니다."
        ));
    }

    /** 실시간(웹소켓) 접속키 발급. local/dev 프로필에서만 200 반환, 그 외 403. */
    @GetMapping("/kis-approval")
    public ResponseEntity<Object> getKisApprovalKey() {
        boolean allowed = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "local".equals(p) || "dev".equals(p));
        if (!allowed) {
            return ResponseEntity.status(403).body(
                    new ErrorResponseDTO(
                            false,
                            "Forbidden in non-dev profile",
                            ApiErrorCodeResolver.FORBIDDEN_IN_NON_DEV_PROFILE
                    )
            );
        }
        String approvalKey = kisApiService.getWebSocketApprovalKey();
        return ResponseEntity.ok(Map.of("approval_key", approvalKey));
    }
}
