package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTestReservationResponseDTO {
    private Long id;
    private String name;
    private String contactType;
    private String contactValue;
    private String resultKey;
    private String resultTitle;
    private String message;
}
