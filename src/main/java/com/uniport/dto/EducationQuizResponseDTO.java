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
public class EducationQuizResponseDTO {
    private String contentVersion;
    private String track;
    private String sector;
    private Integer day;
    private String mode;
    private List<EducationQuizQuestionDTO> questions;
}
