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
public class EducationOverviewDTO {
    private String levelLabel;
    private String dayLabel;
    private String title;
    private String summary1;
    private String summary2;
    private List<String> keyPoints;
    private String ctaLabel;
}
