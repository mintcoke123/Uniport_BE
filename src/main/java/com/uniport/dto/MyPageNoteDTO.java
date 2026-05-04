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
public class MyPageNoteDTO {
    private String investorTypeTitle;
    private String investorTypeDescription;
    private List<String> principles;
    private List<String> recommendedStrategies;
}
