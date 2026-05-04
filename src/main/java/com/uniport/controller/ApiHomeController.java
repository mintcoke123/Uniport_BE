package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.GroupInsightsResponseDTO;
import com.uniport.dto.MockInvestingSummaryResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.HomeDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/home")
@Tag(name = "Mock Investing Home", description = "모의투자 홈 요약 API")
public class ApiHomeController {

    private final HomeDataService homeDataService;
    private final CurrentUserResolver currentUserResolver;

    public ApiHomeController(HomeDataService homeDataService,
                             CurrentUserResolver currentUserResolver) {
        this.homeDataService = homeDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/mock-investing-summary")
    @Operation(summary = "모의투자 홈 통합 요약 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MockInvestingSummaryResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<MockInvestingSummaryResponseDTO> getMockInvestingSummary(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(homeDataService.getSummary(user));
    }

    @GetMapping("/group-matching-dashboard")
    @Operation(summary = "그룹 매칭 홈 대시보드 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<Map<String, Object>> getGroupMatchingDashboard(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(homeDataService.getGroupMatchingDashboard(user));
    }

    @GetMapping("/group-insights")
    @Operation(summary = "상위 그룹 인기 분석 인사이트 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = GroupInsightsResponseDTO.class)))
    })
    public ResponseEntity<GroupInsightsResponseDTO> getGroupInsights() {
        return ResponseEntity.ok(homeDataService.getGroupInsights());
    }
}
