package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationQuizQuestionResultDTO {
    private String questionId;
    private Integer selectedOptionId;
    private Integer correctOptionId;
    private Boolean correct;
}
