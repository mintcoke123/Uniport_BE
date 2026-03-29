package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.SurveyOnboardingRequestDTO;
import com.uniport.dto.SurveyOnboardingResponseDTO;
import com.uniport.service.InvestmentSurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/surveys", "/surveys"})
@Tag(name = "Surveys", description = "설문 제출 API")
public class SurveyController {

    private final InvestmentSurveyService investmentSurveyService;

    public SurveyController(InvestmentSurveyService investmentSurveyService) {
        this.investmentSurveyService = investmentSurveyService;
    }

    @PostMapping("/onboarding")
    @Operation(
            summary = "온보딩 설문 응답 제출",
            description = "Firebase ID Token 인증이 필요합니다. 질문별 응답을 제출하면 투자 성향 결과를 계산하고 현재 사용자에게 저장합니다.",
            security = @SecurityRequirement(name = "firebaseBearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "설문 응답 제출 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SurveyOnboardingResponseDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "id": 2,
                                      "type": "균형 잡힌 판단형",
                                      "title": "균형 잡힌 판단형",
                                      "description": "안정성과 수익 사이의 균형을 중요하게 생각하는 투자 성향입니다.",
                                      "imageUrl": "https://example.com/images/result-balanced.png",
                                      "features": [
                                        {
                                          "title": "나의 투자원칙 TOP 3",
                                          "items": [
                                            {
                                              "name": "분석형 의사결정",
                                              "description": "중요한 투자 결정을 내릴 때 신중하게 판단하는 편입니다."
                                            },
                                            {
                                              "name": "위험 관리",
                                              "description": "손실 가능성을 줄이면서도 적절한 수익을 기대합니다."
                                            },
                                            {
                                              "name": "장기적 관점",
                                              "description": "짧은 변동보다 장기적인 흐름을 더 중요하게 생각합니다."
                                            }
                                          ]
                                        }
                                      ],
                                      "guides": [
                                        {
                                          "title": "내 유형의 특징",
                                          "items": [
                                            {
                                              "name": "리스크 관리 능력",
                                              "description": "과도한 손실을 피하려는 성향이 강합니다."
                                            },
                                            {
                                              "name": "안정적 투자 선호",
                                              "description": "급격한 변동보다 꾸준한 흐름을 선호합니다."
                                            },
                                            {
                                              "name": "분산 투자 적합",
                                              "description": "한 종목에 집중하기보다 나누어 투자하는 방식과 잘 맞습니다."
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 데이터",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Firebase 토큰 없음 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 질문 또는 선택지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    public ResponseEntity<SurveyOnboardingResponseDTO> submitOnboardingSurvey(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser,
            @RequestBody SurveyOnboardingRequestDTO request) {
        return ResponseEntity.ok(investmentSurveyService.submitOnboarding(
                authenticatedUser != null ? authenticatedUser.getUser() : null,
                request));
    }
}
