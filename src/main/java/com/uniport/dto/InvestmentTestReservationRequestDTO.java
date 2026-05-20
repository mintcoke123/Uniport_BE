package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTestReservationRequestDTO {
    private String name;
    private String contact;
    private Boolean consent;
    private String resultKey;
    private String resultTitle;
    private List<String> interestKeywords;
    private Map<String, Object> answers;
}
