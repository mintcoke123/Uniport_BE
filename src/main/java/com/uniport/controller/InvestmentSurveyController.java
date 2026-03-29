package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.dto.InvestmentSurveyQuestionsResponseDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment-survey")
@Tag(name = "Investment Survey", description = "투자 성향 설문 API")
public class InvestmentSurveyController {

    private final InvestmentSurveyService investmentSurveyService;

    public InvestmentSurveyController(InvestmentSurveyService investmentSurveyService) {
        this.investmentSurveyService = investmentSurveyService;
    }

    @GetMapping("/questions")
    @Operation(
            summary = "투자 성향 설문 질문 조회",
            description = "Firebase ID Token 인증이 필요합니다. 현재 사용자가 이미 투자 성향 결과를 가지고 있으면 질문 대신 결과 보유 상태를 반환합니다.",
            security = @SecurityRequirement(name = "firebaseBearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "설문 질문 조회 성공 또는 이미 결과가 있는 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InvestmentSurveyQuestionsResponseDTO.class),
                            examples = {
                                    @ExampleObject(
                                            name = "QuestionsResponse",
                                            value = """
                                                    {
                                                      "questions": [
                                                        {
                                                          "id": 1,
                                                          "order": 1,
                                                          "title": "100만원이 1주일 사이 5만원(5%) 떨어졌어.",
                                                          "subtitle": "나랑 제일 가까운 반응은?",
                                                          "options": [
                                                            {
                                                              "id": 1,
                                                              "label": "바로 팔고 다시는 안 한다",
                                                              "sublabel": "손실을 더 보고 싶지 않다"
                                                            },
                                                            {
                                                              "id": 2,
                                                              "label": "이유를 찾아보고 조금 더 지켜본다",
                                                              "sublabel": "상황을 보고 판단한다"
                                                            },
                                                            {
                                                              "id": 3,
                                                              "label": "오히려 추가 매수 기회라고 본다",
                                                              "sublabel": "장기적으로 다시 오를 수 있다"
                                                            }
                                                          ]
                                                        },
                                                        {
                                                          "id": 2,
                                                          "order": 2,
                                                          "title": "가격 변동을 얼마나 자주 보고 싶은 편인가요?",
                                                          "subtitle": null,
                                                          "options": [
                                                            {
                                                              "id": 4,
                                                              "label": "자주 보면 불안해서 싫다",
                                                              "sublabel": null
                                                            },
                                                            {
                                                              "id": 5,
                                                              "label": "하루 한 번 정도는 괜찮다",
                                                              "sublabel": null
                                                            },
                                                            {
                                                              "id": 6,
                                                              "label": "수시로 보는 편이 편하다",
                                                              "sublabel": null
                                                            }
                                                          ]
                                                        }
                                                      ],
                                                      "hasResult": false,
                                                      "investmentProfileResult": null,
                                                      "message": "투자 성향 설문 질문 조회 성공"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "AlreadyHasResult",
                                            value = """
                                                    {
                                                      "questions": [],
                                                      "hasResult": true,
                                                      "investmentProfileResult": "BALANCED",
                                                      "message": "이미 투자 성향 결과가 있습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Firebase 토큰 없음 또는 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Authorization Bearer token is required",
                                      "requestId": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "설문 질문 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    public ResponseEntity<InvestmentSurveyQuestionsResponseDTO> getInvestmentSurveyQuestions(
            @AuthenticationPrincipal FirebaseAuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(investmentSurveyService.getQuestions(
                authenticatedUser != null ? authenticatedUser.getUser() : null));
    }
}
