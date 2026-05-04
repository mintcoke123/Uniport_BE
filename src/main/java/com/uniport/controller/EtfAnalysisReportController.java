package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.EtfAnalysisReportResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.EtfDataService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etf-analysis-reports")
@Tag(name = "ETF Analysis Reports", description = "ETF 분석 리포트 조회 API")
public class EtfAnalysisReportController {

    private final EtfDataService etfDataService;
    private final CurrentUserResolver currentUserResolver;

    public EtfAnalysisReportController(EtfDataService etfDataService, CurrentUserResolver currentUserResolver) {
        this.etfDataService = etfDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "ETF 분석 리포트 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EtfAnalysisReportResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리포트",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfAnalysisReportResponseDTO> getAnalysisReport(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String reportId,
            @RequestParam(value = "period", required = false) String period) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfDataService.getReport(user, reportId, period));
    }
}
