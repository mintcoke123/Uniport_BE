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
@Schema(description = "모의투자 홈 CTA")
public class MockInvestmentCtaDTO {

    @Schema(example = "투자하러 가기", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(example = "OPEN_TRADING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean enabled;
}
