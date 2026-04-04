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
@Schema(description = "학습 로드맵 Day 상태")
public class LearningRoadmapItemDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer day;

    @Schema(example = "CURRENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "오늘 학습 진행중", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusLabel;
}
