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
    private String templateType;
    private String rendererType;
    private String visualType;
    private String visualKey;
    private String componentKey;
    private String assetKey;
    private String imageDelivery;
    private String imageUrl;
    private JsonNode visual;
    private JsonNode visualPayload;
    private JsonNode renderPolicy;
}
