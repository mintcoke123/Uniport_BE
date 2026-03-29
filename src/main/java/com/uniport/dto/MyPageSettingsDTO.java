package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "마이페이지 간단 설정 정보")
public class MyPageSettingsDTO {

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pushEnabled;
}
