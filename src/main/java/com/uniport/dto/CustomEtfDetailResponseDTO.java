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
@Schema(description = "나만의 ETF 상세 응답")
public class CustomEtfDetailResponseDTO {

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "AI 테크", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @ArraySchema(schema = @Schema(implementation = CustomEtfHoldingDTO.class))
    private List<CustomEtfHoldingDTO> items;
}
