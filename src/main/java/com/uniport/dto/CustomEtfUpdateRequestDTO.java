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
@Schema(description = "나만의 ETF 수정 요청")
public class CustomEtfUpdateRequestDTO {

    @Schema(example = "AI 테크 성장형")
    private String title;

    @ArraySchema(schema = @Schema(implementation = CustomEtfItemRequestDTO.class))
    private List<CustomEtfItemRequestDTO> items;
}
