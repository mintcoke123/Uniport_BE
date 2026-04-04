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
@Schema(description = "인기 ETF 상세 응답")
public class EtfDiscoveryDetailResponseDTO {

    @Schema(example = "ETF_901", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "AI 테크", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "성장 중심", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subtitle;

    @Schema(example = "전 세계 AI 혁명을 주도하는 반도체 및 소프트웨어 핵심 기업 7곳에 집중 투자하는 포트폴리오입니다.")
    private String description;

    @Schema(example = "인기")
    private String badgeLabel;

    @ArraySchema(schema = @Schema(example = "반도체"))
    private List<String> tags;

    @Schema(example = "24.8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double recentReturnRate3M;

    @Schema(example = "높음", requiredMode = Schema.RequiredMode.REQUIRED)
    private String riskLevel;

    @Schema(example = "1Y", requiredMode = Schema.RequiredMode.REQUIRED)
    private String period;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean favorite;

    @Schema(example = "12500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer favoriteCount;

    @Schema(example = "https://example.com/ai-tech.png")
    private String thumbnailUrl;

    @ArraySchema(schema = @Schema(implementation = EtfDiscoveryTrendPointDTO.class))
    private List<EtfDiscoveryTrendPointDTO> trend;

    @ArraySchema(schema = @Schema(implementation = EtfDiscoveryDetailHoldingDTO.class))
    private List<EtfDiscoveryDetailHoldingDTO> holdings;
}
