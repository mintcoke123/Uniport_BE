package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorSentimentDTO {

    private Integer bullishCount;
    private Integer bearishCount;
    private Integer neutralCount;
    private Integer bullishPercentage;
    private Integer bearishPercentage;
    private Integer neutralPercentage;
}
