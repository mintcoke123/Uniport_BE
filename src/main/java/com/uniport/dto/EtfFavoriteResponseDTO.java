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
@Schema(description = "ETF 좋아요 응답")
public class EtfFavoriteResponseDTO {

    @Schema(example = "ETF_901", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean favorite;

    @Schema(example = "12501", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer favoriteCount;

    @Schema(example = "관심 ETF에 추가되었어요.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
