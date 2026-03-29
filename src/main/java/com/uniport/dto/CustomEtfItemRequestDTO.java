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
@Schema(description = "나만의 ETF 구성 종목 요청")
public class CustomEtfItemRequestDTO {

    @Schema(example = "US_AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockId;

    @Schema(example = "40", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer weight;
}
