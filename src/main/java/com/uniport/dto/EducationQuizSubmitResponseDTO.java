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
public class EducationQuizSubmitResponseDTO {
    private String track;
    private String sector;
    private Integer day;
    private String mode;
    private boolean submitted;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer correctCount;
    private boolean dayReadyToComplete;
    private boolean dayCompleted;
    private List<EducationQuizQuestionResultDTO> results;
}
