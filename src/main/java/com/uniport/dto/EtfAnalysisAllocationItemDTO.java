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
@Schema(description = "자산 비중 아이템")
public class EtfAnalysisAllocationItemDTO {

    @Schema(example = "Apple Inc.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer weight;
}
