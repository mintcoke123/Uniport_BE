package com.uniport.dto;

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
@Schema(description = "포트폴리오 적합 종목 추천 응답")
public class EtfPortfolioFitRecommendationResponseDTO {

    @ArraySchema(schema = @Schema(implementation = EtfPortfolioFitRecommendationItemDTO.class))
    private List<EtfPortfolioFitRecommendationItemDTO> items;
}
