package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationDayCompleteResponseDTO {
    private String track;
    private String sector;
    private Integer day;
    private boolean completed;
    private Integer streakDays;
    private Integer earnedPoint;
    private Integer earnedExp;
    private Integer nextDay;
    private String completionTitle;
    private String completionDescription;
}
