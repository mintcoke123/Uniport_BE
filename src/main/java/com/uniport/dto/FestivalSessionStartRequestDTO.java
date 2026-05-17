package com.uniport.dto;

import lombok.Data;

@Data
public class FestivalSessionStartRequestDTO {
    private String name;
    private String phoneNumber;
    private Boolean privacyAgreed;
}
