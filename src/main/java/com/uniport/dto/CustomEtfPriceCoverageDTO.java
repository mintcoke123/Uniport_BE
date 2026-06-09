package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "나만의 ETF 가격 캐시 기간별 커버리지")
public class CustomEtfPriceCoverageDTO {

    @Schema(example = "1Y", allowableValues = {"1Y", "3Y", "5Y"})
    private String period;

    @Schema(example = "READY", allowableValues = {"READY", "PARTIAL", "PENDING", "UNAVAILABLE"})
    private String status;

    @Schema(example = "2025-05-01", description = "캐시된 가격 시작일. 캐시가 없으면 null")
    private LocalDate availableFrom;

    @Schema(example = "2026-05-01", description = "캐시된 가격 종료일. 캐시가 없으면 null")
    private LocalDate availableTo;

    @Schema(example = "243", description = "캐시된 일별 가격 row 수")
    private Long priceCount;

    @Schema(example = "분석 시점에 실가격을 확인합니다.", description = "상태 설명")
    private String message;
}
