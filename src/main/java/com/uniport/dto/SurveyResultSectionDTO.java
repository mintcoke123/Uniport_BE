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
@Schema(description = "투자 성향 결과 섹션")
public class SurveyResultSectionDTO {

    @Schema(description = "섹션 제목", example = "나의 투자원칙 TOP 3", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @ArraySchema(schema = @Schema(implementation = SurveyResultDetailItemDTO.class))
    private List<SurveyResultDetailItemDTO> items;
}
