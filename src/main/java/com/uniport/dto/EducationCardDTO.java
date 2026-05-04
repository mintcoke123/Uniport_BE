package com.uniport.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationCardDTO {
    private Integer idx;
    private String sheet;
    private String track;
    private String sector;
    private Integer day;
    private String section;
    private String cardNumber;
    private String assetId;
    private String title;
    private String text;
    private String imageType;
    private String svgPreset;
    private JsonNode visual;
}
