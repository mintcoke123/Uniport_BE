package com.uniport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "포트폴리오 적합 종목 추천 요청")
public class EtfPortfolioFitRecommendationRequestDTO {

    @Schema(example = "ETF_CUSTOM")
    private String customEtfId;

    @Schema(example = "REPORT_ABCD1234")
    private String analysisReportId;

    @ArraySchema(schema = @Schema(implementation = CustomEtfItemRequestDTO.class))
    private List<CustomEtfItemRequestDTO> items;

    @Schema(example = "3", minimum = "1", maximum = "10")
    private Integer limit;

    @Schema(example = "ALL", allowableValues = {"ALL", "KRX", "US", "KOSPI", "KOSDAQ", "NASDAQ", "NYSE", "AMEX"})
    private String market;
}
