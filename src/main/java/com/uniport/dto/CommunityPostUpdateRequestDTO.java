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
@Schema(description = "Community post update request")
public class CommunityPostUpdateRequestDTO {

    @Schema(example = "수정된 제목")
    private String title;

    @Schema(example = "수정된 본문")
    private String content;

    @Schema(example = "005930")
    private String stockCode;

    @Schema(example = "삼성전자")
    private String stockName;

    @Schema(example = "BULLISH")
    private String sentiment;
}
