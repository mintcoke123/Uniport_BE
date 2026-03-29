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
@Schema(description = "현재 진행 중이거나 대기 중인 매칭방 요약")
public class HomeActiveMatchDTO {

    @Schema(example = "12", nullable = true)
    private Long roomId;

    @Schema(example = "상시 그룹 매칭", nullable = true)
    private String title;

    @Schema(example = "WAITING", nullable = true)
    private String status;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean startable;
}
