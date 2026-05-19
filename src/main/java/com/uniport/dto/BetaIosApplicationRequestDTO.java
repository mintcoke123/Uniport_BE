package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetaIosApplicationRequestDTO {
    private String name;
    private String appleIdEmail;
    private String contactEmail;
    private String device;
    private Boolean consent;
}
