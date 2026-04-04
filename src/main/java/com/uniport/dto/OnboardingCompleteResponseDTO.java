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
@Schema(description = "온보딩 완료 응답")
public class OnboardingCompleteResponseDTO {

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean completed;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean noteCreated;

    @Schema(example = "첫 투자 노트가 생성되었어요", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(example = "30일 투자 공부 하러가기", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nextActionLabel;
}
