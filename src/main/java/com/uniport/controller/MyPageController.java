package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PointSocialDataService;
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

@RestController
@RequestMapping("/api/mypage")
@Tag(name = "MyPage", description = "마이페이지 API")
public class MyPageController {

    private final PointSocialDataService pointSocialDataService;
    private final CurrentUserResolver currentUserResolver;

    public MyPageController(PointSocialDataService pointSocialDataService,
                            CurrentUserResolver currentUserResolver) {
        this.pointSocialDataService = pointSocialDataService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "마이페이지 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPageResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<MyPageResponseDTO> getMyPage(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(pointSocialDataService.getMyPage(user));
    }
}
