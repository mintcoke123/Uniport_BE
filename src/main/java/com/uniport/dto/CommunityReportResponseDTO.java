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
@Schema(description = "커뮤니티 신고 응답")
public class CommunityReportResponseDTO {

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean reported;

    @Schema(example = "2026-03-11T08:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
