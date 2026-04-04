package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.EtfDiscoveryDetailResponseDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.dto.EtfFavoriteResponseDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etf-discovery")
@Tag(name = "ETF Discovery", description = "인기 ETF 탐색 API")
public class EtfDiscoveryController {

    private final EtfMockService etfMockService;
    private final CurrentUserResolver currentUserResolver;

    public EtfDiscoveryController(EtfMockService etfMockService, CurrentUserResolver currentUserResolver) {
        this.etfMockService = etfMockService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/popular")
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

    @GetMapping("/{etfId}")
    @Operation(summary = "인기 ETF 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EtfDiscoveryDetailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfDiscoveryDetailResponseDTO> getDiscoveryDetail(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId,
            @RequestParam(value = "period", required = false) String period) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(etfMockService.getDiscoveryDetail(etfId, period, user));
    }

    @PostMapping("/{etfId}/favorite")
    @Operation(summary = "인기 ETF 좋아요", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 성공",
                    content = @Content(schema = @Schema(implementation = EtfFavoriteResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfFavoriteResponseDTO> favoriteDiscoveryEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.favoriteDiscoveryEtf(user, etfId, true));
    }

    @DeleteMapping("/{etfId}/favorite")
    @Operation(summary = "인기 ETF 좋아요 취소", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 취소 성공",
                    content = @Content(schema = @Schema(implementation = EtfFavoriteResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 ETF",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<EtfFavoriteResponseDTO> unfavoriteDiscoveryEtf(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String etfId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(etfMockService.favoriteDiscoveryEtf(user, etfId, false));
    }
}
