package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/stocks/search 응답 항목.
 * id는 code를 Long으로 파싱한 값(예: "005930" → 5930). 프론트 /stock-detail?id= 사용.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSearchItemDTO {

    private Long id;
    private String code;
    private String name;
    private String market;
}
