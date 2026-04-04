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
@Schema(description = "ETF 공유 요청")
public class EtfShareRequestDTO {

    @Schema(example = "COMMUNITY", allowableValues = {"COMMUNITY", "CHAT"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetType;

    @Schema(example = "REPORT_301")
    private String reportId;

    @Schema(example = "7")
    private Long roomId;
}
