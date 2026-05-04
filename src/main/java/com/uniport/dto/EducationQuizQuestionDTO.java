package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationQuizQuestionDTO {
    private String id;
    private Integer quizNumber;
    private String quizType;
    private String question;
    private List<EducationQuizOptionDTO> options;
    private Integer answerIndex;
    private String topic;
    private String area;
    private String intent;
}
