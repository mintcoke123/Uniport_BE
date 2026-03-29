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
@Schema(description = "마이페이지 요약 정보")
public class MyPageSummaryDTO {

    @Schema(example = "760", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer learningTimeMinutes;

    @Schema(example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentStreak;
}
