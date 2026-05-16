package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPageCharacterCardDTO {
    private String code;
    private String name;
    private String emoji;
    private String themeColor;
    private Integer stage;
    private String assetKey;
    private Boolean selected;
}
