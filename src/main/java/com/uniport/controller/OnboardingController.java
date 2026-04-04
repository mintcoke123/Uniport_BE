package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.OnboardingCompleteResponseDTO;
import com.uniport.dto.OnboardingNicknameUpdateRequestDTO;
import com.uniport.dto.OnboardingNicknameUpdateResponseDTO;
import com.uniport.dto.OnboardingSurveyFlowResponseDTO;
import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.OnboardingSurveySubmitRequestDTO;
import com.uniport.service.OnboardingService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@Tag(name = "Onboarding", description = "온보딩 설문 및 프로필 API")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/survey")
    @Operation(summary = "온보딩 설문 플로우 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<OnboardingSurveyFlowResponseDTO> getSurveyFlow(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(onboardingService.getSurveyFlow(
                authenticatedUser != null ? authenticatedUser.getUser() : null));
    }

    @PatchMapping("/profile/nickname")
    @Operation(summary = "온보딩 닉네임 저장", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<OnboardingNicknameUpdateResponseDTO> updateNickname(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestBody OnboardingNicknameUpdateRequestDTO request) {
        return ResponseEntity.ok(onboardingService.updateNickname(
                authenticatedUser != null ? authenticatedUser.getUser() : null,
                request));
    }

    @PostMapping("/survey-results")
    @Operation(summary = "온보딩 설문 제출", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<OnboardingSurveyResultDTO> submitSurvey(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestBody OnboardingSurveySubmitRequestDTO request) {
        return ResponseEntity.ok(onboardingService.submitSurvey(
                authenticatedUser != null ? authenticatedUser.getUser() : null,
                request));
    }

    @GetMapping("/survey-results/me")
    @Operation(summary = "내 온보딩 설문 결과 조회", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<OnboardingSurveyResultDTO> getMyResult(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(onboardingService.getMyResult(
                authenticatedUser != null ? authenticatedUser.getUser() : null));
    }

    @PostMapping("/complete")
    @Operation(summary = "온보딩 완료 처리", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 성공",
                    content = @Content(schema = @Schema(implementation = OnboardingCompleteResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<OnboardingCompleteResponseDTO> complete(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(onboardingService.complete(
                authenticatedUser != null ? authenticatedUser.getUser() : null));
    }
}
