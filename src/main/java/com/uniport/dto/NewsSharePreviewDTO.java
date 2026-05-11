package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "채팅 뉴스 공유 카드 데이터")
public class NewsSharePreviewDTO {

    @Schema(example = "news_001")
    private String id;

    @Schema(example = "시황")
    private String categoryLabel;

    @Schema(example = "코스피, 반도체 강세에 장 초반 상승 출발")
    private String title;

    @Schema(example = "외국인 순매수와 대형 기술주 반등이 지수 흐름을 이끌고 있어요.")
    private String summary;
}
