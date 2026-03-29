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
@Schema(description = "나만의 ETF 생성/수정 응답")
public class CustomEtfMutationResponseDTO {

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "AI 테크", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalWeight;

    @Schema(example = "2026-03-11T16:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;

    @Schema(example = "2026-03-11T16:10:00Z")
    private String updatedAt;
}
