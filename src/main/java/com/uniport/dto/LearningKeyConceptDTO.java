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
@Schema(description = "핵심 개념 항목")
public class LearningKeyConceptDTO {

    @Schema(example = "시가, 종가, 고가, 저가의 정의", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
}
