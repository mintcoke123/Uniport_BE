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
@Schema(description = "ETF 공유 응답")
public class EtfShareResponseDTO {

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "COMMUNITY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetType;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean shared;

    @Schema(example = "커뮤니티에 포트폴리오 공유 준비가 완료되었어요.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "프론트에서 공유 화면을 이어서 열어주세요.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
}
