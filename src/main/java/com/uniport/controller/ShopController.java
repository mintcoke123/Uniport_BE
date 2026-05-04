package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.ShopItemsResponseDTO;
import com.uniport.dto.ShopRedemptionDetailResponseDTO;
import com.uniport.dto.ShopRedemptionListResponseDTO;
import com.uniport.dto.ShopRedemptionPreviewResponseDTO;
import com.uniport.dto.ShopRedemptionRequestDTO;
import com.uniport.dto.ShopRedemptionResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PointSocialDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
@Tag(name = "Shop", description = "포인트샵 API")
public class ShopController {

    private final PointSocialDataService pointSocialDataService;
    private final CurrentUserResolver currentUserResolver;

    public ShopController(PointSocialDataService pointSocialDataService,
                          CurrentUserResolver currentUserResolver) {
        this.pointSocialDataService = pointSocialDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/items")
    @Operation(summary = "포인트샵 상품 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ShopItemsResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 카테고리/정렬/페이지",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<ShopItemsResponseDTO> getShopItems(
            @Parameter(example = "CAFE")
            @RequestParam(value = "category", required = false) String category,
            @Parameter(example = "POPULAR")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(pointSocialDataService.getShopItems(category, sort, page, size));
    }

    @GetMapping("/redemptions")
    @Operation(summary = "교환 내역 목록 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ShopRedemptionListResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<ShopRedemptionListResponseDTO> getRedemptions(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getRedemptions(user));
    }

    @GetMapping("/redemptions/{redemptionId}")
    @Operation(summary = "교환 내역 상세 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ShopRedemptionDetailResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "교환 내역 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<ShopRedemptionDetailResponseDTO> getRedemptionDetail(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String redemptionId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getRedemptionDetail(user, redemptionId));
    }

    @GetMapping("/redemptions/preview")
    @Operation(summary = "포인트샵 교환 확인 정보 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ShopRedemptionPreviewResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<ShopRedemptionPreviewResponseDTO> getRedemptionPreview(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("itemId") String itemId) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getRedemptionPreview(user, itemId));
    }

    @PostMapping("/redemptions")
    @Operation(summary = "기프티콘 교환 최종 요청", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "교환 요청 생성 성공",
                    content = @Content(schema = @Schema(implementation = ShopRedemptionResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "재고 부족 또는 포인트 부족",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<ShopRedemptionResponseDTO> redeem(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShopRedemptionRequestDTO request) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(pointSocialDataService.redeem(user, request));
    }
}
