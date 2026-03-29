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
@Schema(description = "자산 비중 정보")
public class EtfAnalysisAllocationDTO {

    @ArraySchema(schema = @Schema(implementation = EtfAnalysisAllocationItemDTO.class))
    private List<EtfAnalysisAllocationItemDTO> items;
}
