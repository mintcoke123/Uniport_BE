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

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "조심스러운 거북이형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "조심스러운 거북이형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "원금은 지키면서 천천히 배우고 싶은 장기형 투자자시네요!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(example = "https://example.com/images/result-turtle.png", nullable = true)
    private String imageUrl;

    @Schema(example = "Lv.1", nullable = true)
    private String levelLabel;

    @Schema(example = "이런 투자자일 확률이 높아요!", nullable = true)
    private String probabilityLabel;

    @Schema(example = "추천 전략", nullable = true)
    private String strategyTitle;

    @Schema(example = "코어+위성 조합", nullable = true)
    private String strategyLabel;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> features;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> guides;
}
