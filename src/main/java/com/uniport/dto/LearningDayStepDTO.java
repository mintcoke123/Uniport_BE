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
@Schema(description = "학습 Day step")
public class LearningDayStepDTO {

    @Schema(example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer order;

    @Schema(example = "THEORY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "CHAPTER 02", nullable = true)
    private String chapter;

    @Schema(example = "캔들스틱의 기본 구조", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "캔들스틱은 시가, 종가, 고가, 저가를 하나의 막대로 표현합니다.", nullable = true)
    private String description;

    @Schema(example = "https://example.com/candle.png", nullable = true)
    private String imageUrl;

    @Schema(example = "다음 중 캔들스틱에서 몸통이 나타내는 것은?", nullable = true)
    private String question;

    @ArraySchema(schema = @Schema(implementation = LearningStepOptionDTO.class))
    private List<LearningStepOptionDTO> options;
}
