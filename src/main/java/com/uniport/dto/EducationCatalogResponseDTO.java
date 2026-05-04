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
public class EducationCatalogResponseDTO {
    private String contentVersion;
    private List<EducationTrackSummaryDTO> tracks;
    private List<String> sectors;
}
