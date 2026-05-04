package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "마이페이지 조회 응답")
public class MyPageResponseDTO {

    private MyPageUserDTO user;

    private MyPageExpDTO exp;

    private MyPageSummaryDTO summary;

    private MyPageSettingsDTO settings;

    private MyPageAssetSummaryDTO assets;

    private List<MyPageCharacterCardDTO> characters;

    private List<MyPageBadgeDTO> badges;
}
