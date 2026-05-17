package com.uniport.dto;

import lombok.Data;

@Data
public class FestivalSessionStartRequestDTO {
    private String department;
    private String studentId;
    private String name;
    private String phoneNumber;
    private Boolean privacyAgreed;
}
