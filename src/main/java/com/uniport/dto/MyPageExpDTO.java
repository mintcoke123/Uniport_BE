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
@Schema(description = "마이페이지 경험치 정보")
public class MyPageExpDTO {

    @Schema(example = "640", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentExp;

    @Schema(example = "300", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer maxExp;

    @Schema(example = "8940", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalExp;

    @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer maxLevel;
}
