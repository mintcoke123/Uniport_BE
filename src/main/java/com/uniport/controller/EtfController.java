package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfListResponseDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
import com.uniport.dto.CustomEtfUpdateRequestDTO;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.EtfAnalysisApplyRequestDTO;
import com.uniport.dto.EtfAnalysisApplyResponseDTO;
import com.uniport.dto.EtfAnalysisReportResponseDTO;
import com.uniport.dto.EtfAnalysisRequestDTO;
import com.uniport.dto.EtfAnalysisStartResponseDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.EtfMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "ETF", description = "나만의 ETF 및 분석 리포트 API")
public class EtfController {

    private final EtfMockService etfMockService;
    private final CurrentUserResolver currentUserResolver;

    public EtfController(EtfMockService etfMockService, CurrentUserResolver currentUserResolver) {
        this.etfMockService = etfMockService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/custom-etfs")
    @Operation(summary = "나만의 ETF 목록 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CustomEtfListResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CustomEtfListResponseDTO> getCustomEtfs(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.getCustomEtfs(user));
    }

    @PostMapping("/custom-etfs")
    @Operation(summary = "나만의 ETF 생성", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = CustomEtfMutationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CustomEtfMutationResponseDTO> createCustomEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CustomEtfCreateRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.createCustomEtf(user, request));
    }

    @GetMapping("/custom-etfs/{etfId}")
    @Operation(summary = "나만의 ETF 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CustomEtfDetailResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CustomEtfDetailResponseDTO> getCustomEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.getCustomEtf(user, etfId));
    }

    @PutMapping("/custom-etfs/{etfId}")
    @Operation(summary = "나만의 ETF 수정", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = CustomEtfMutationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CustomEtfMutationResponseDTO> updateCustomEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId,
            @RequestBody CustomEtfUpdateRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.updateCustomEtf(user, etfId, request));
    }

    @PostMapping("/custom-etfs/{etfId}/analysis")
    @Operation(summary = "포트폴리오 분석", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 성공",
                    content = @Content(schema = @Schema(implementation = EtfAnalysisStartResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfAnalysisStartResponseDTO> analyzeCustomEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId,
            @RequestBody EtfAnalysisRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.analyze(user, etfId, request));
    }

    @GetMapping("/etf-analysis-reports/{reportId}")
    @Operation(summary = "포트폴리오 분석 리포트 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
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
            @PathVariable String reportId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.getReport(user, reportId));
    }

    @PostMapping("/custom-etfs/{etfId}/analysis-reports/{reportId}/apply")
    @Operation(summary = "분석 리포트 ETF 적용", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적용 성공",
                    content = @Content(schema = @Schema(implementation = EtfAnalysisApplyResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF 또는 리포트",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfAnalysisApplyResponseDTO> applyAnalysisReport(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId,
            @PathVariable String reportId,
            @RequestBody EtfAnalysisApplyRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.applyReport(user, etfId, reportId, request));
    }

    @GetMapping("/etf-discovery/popular")
    @Operation(summary = "인기 ETF 탐색 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EtfDiscoveryResponseDTO.class)))
    })
    public ResponseEntity<EtfDiscoveryResponseDTO> getPopularEtfs(
            @Parameter(example = "RETURN", description = "정렬 기준: RETURN, POPULAR")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(example = "기술", description = "테마 필터")
            @RequestParam(value = "theme", required = false) String theme,
            @Parameter(example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(etfMockService.getPopularEtfs(sort, theme, page, size));
    }
}
