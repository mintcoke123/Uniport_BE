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
@Schema(description = "진행 정보")
public class LearningProgressDTO {

    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer current;

    @Schema(example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer total;
}
