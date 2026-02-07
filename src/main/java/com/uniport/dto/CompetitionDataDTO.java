package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 명세 §2-1: competitionData */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitionDataDTO {

    private String name;
    private String endDate;   // ISO date
    private Integer daysRemaining;
}
