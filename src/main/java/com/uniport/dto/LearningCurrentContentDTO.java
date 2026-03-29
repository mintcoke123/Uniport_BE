package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "현재 학습 콘텐츠 정보")
public class LearningCurrentContentDTO {

    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer day;

    @Schema(example = "캔들스틱 차트의 이해", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "CURRENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
