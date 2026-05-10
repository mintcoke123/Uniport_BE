package com.uniport.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
@Schema(description = "나만의 ETF 수정 요청")
public class CustomEtfUpdateRequestDTO {

    @Schema(example = "AI 테크 성장형")
    @JsonAlias("name")
    private String title;

    @ArraySchema(schema = @Schema(implementation = CustomEtfItemRequestDTO.class))
    @JsonAlias("stocks")
    private List<CustomEtfItemRequestDTO> items;
}
