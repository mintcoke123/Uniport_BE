package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "온보딩 투자 성향 결과")
public class OnboardingSurveyResultDTO {

    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long characterId;

    @Schema(example = "성실한 농부", requiredMode = Schema.RequiredMode.REQUIRED)
    private String characterName;

    @Schema(example = "🌾", requiredMode = Schema.RequiredMode.REQUIRED)
    private String characterEmoji;

    @Schema(example = "#4A4A4A", requiredMode = Schema.RequiredMode.REQUIRED)
    private String characterColor;

    @Schema(example = "성실한 농부", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "성실한 농부", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "현재 응답 기준으로 가장 가까운 투자 캐릭터는 성실한 농부형이야.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(example = "https://example.com/images/result-farmer.png", nullable = true)
    private String imageUrl;

    @Schema(example = "입문", nullable = true)
    private String levelLabel;

    @Schema(example = "입문", nullable = true)
    private String investmentLevel;

    @Schema(example = "AI 반도체", nullable = true)
    private String interestSector;

    @Schema(example = "규칙적으로 모으고 오래 운영하는 적립식 장기 복리형 투자자", nullable = true)
    private String investmentType;

    @Schema(example = "너의 투자레벨은 입문, 현재 가장 관심 있는 섹터는 AI 반도체야.", nullable = true)
    private String probabilityLabel;

    @Schema(example = "추천 전략", nullable = true)
    private String strategyTitle;

    @Schema(example = "월간 자동매수와 정기 리밸런싱", nullable = true)
    private String strategyLabel;

    @ArraySchema(schema = @Schema(example = "투자에서 가장 큰 무기는 타이밍이 아니라 꾸준함이라고 믿는 편이다."))
    private List<String> traits;

    @ArraySchema(schema = @Schema(example = "월간 자동매수와 정기 리밸런싱을 기본 전략으로 둔다."))
    private List<String> recommendedStrategies;

    @ArraySchema(schema = @Schema(example = "시장 상황과 무관하게 정한 적립일은 지킨다."))
    private List<String> personalPrinciples;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> features;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> guides;
}
