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
@Schema(description = "나만의 ETF 목록 응답")
public class CustomEtfListResponseDTO {

    @ArraySchema(schema = @Schema(implementation = CustomEtfSummaryDTO.class))
    private List<CustomEtfSummaryDTO> items;
}
