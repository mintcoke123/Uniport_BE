package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfListResponseDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
import com.uniport.dto.CustomEtfUpdateRequestDTO;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.EtfAnalysisApplyRequestDTO;
import com.uniport.dto.EtfAnalysisApplyResponseDTO;
import com.uniport.dto.EtfAnalysisRequestDTO;
import com.uniport.dto.EtfAnalysisStartResponseDTO;
import com.uniport.dto.EtfShareRequestDTO;
import com.uniport.dto.EtfShareResponseDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom-etfs")
@Tag(name = "Custom ETFs", description = "나만의 ETF 생성, 수정, 분석 API")
public class CustomEtfController {

    private final EtfDataService etfDataService;
    private final CurrentUserResolver currentUserResolver;

    public CustomEtfController(EtfDataService etfDataService, CurrentUserResolver currentUserResolver) {
        this.etfDataService = etfDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
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
        return ResponseEntity.ok(etfDataService.getCustomEtfs(user));
    }

    @PostMapping
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
        return ResponseEntity.ok(etfDataService.createCustomEtf(user, request));
    }

    @GetMapping("/assets/search")
    @Operation(summary = "나만의 ETF 구성 주식 검색", description = "직접 구성 플로우는 검증된 주식형 자산만 지원합니다.",
            security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공",
                    content = @Content(schema = @Schema(implementation = CustomEtfAssetSearchResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<CustomEtfAssetSearchResponseDTO> searchAssets(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "assetType", required = false) String assetType,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfDataService.searchAssets(keyword, assetType, market, page, size));
    }

    @GetMapping("/{etfId}")
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
        return ResponseEntity.ok(etfDataService.getCustomEtf(user, etfId));
    }

    @PutMapping("/{etfId}")
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
        return ResponseEntity.ok(etfDataService.updateCustomEtf(user, etfId, request));
    }

    @PostMapping("/{etfId}/analysis")
    @Operation(summary = "나만의 ETF 분석 요청", security = @SecurityRequirement(name = "firebaseBearerAuth"))
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
            @RequestBody(required = false) EtfAnalysisRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfDataService.analyze(user, etfId, request));
    }

    @PostMapping("/{etfId}/analysis-reports/{reportId}/apply")
    @Operation(summary = "분석 리포트 적용", security = @SecurityRequirement(name = "firebaseBearerAuth"))
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
        return ResponseEntity.ok(etfDataService.applyReport(user, etfId, reportId, request));
    }

    @PostMapping("/{etfId}/share")
    @Operation(summary = "ETF 공유 준비", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공유 준비 성공",
                    content = @Content(schema = @Schema(implementation = EtfShareResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfShareResponseDTO> shareCustomEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId,
            @RequestBody EtfShareRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfDataService.shareCustomEtf(user, etfId, request));
    }
}
