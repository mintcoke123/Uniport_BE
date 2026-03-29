package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "온보딩 설문 제출 요청")
public class SurveyOnboardingRequestDTO {

    @ArraySchema(schema = @Schema(implementation = SurveyAnswerItemDTO.class))
    private List<SurveyAnswerItemDTO> answers;
}
