package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationTrackSummaryDTO {
    private String track;
    private String levelLabel;
    private String title;
    private Integer totalDays;
    private String sector;
}
