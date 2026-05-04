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
public class EducationDayContentResponseDTO {
    private String contentVersion;
    private String track;
    private String sector;
    private Integer day;
    private EducationOverviewDTO overview;
    private List<EducationCardDTO> cards;
    private EducationQuizMetaDTO quiz;
}
