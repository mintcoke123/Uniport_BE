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
@Schema(description = "백테스트 기반 AI 피드백")
public class EtfAnalysisAiFeedbackDTO {

    @Schema(example = "AI 리스크 진단")
    private String title;

    @Schema(example = "백테스트 기준 1년 동안 원금 대비 8.4%의 수익 구간이 관찰됐어요.")
    private String summary;

    private List<EtfAnalysisFeedbackBulletDTO> bullets;

    @Schema(example = "CAUTION", allowableValues = {"BALANCED", "CAUTION"})
    private String tone;

    @Schema(example = "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.")
    private String disclaimer;

    @Schema(example = "true", description = "LLM 문장화가 아닌 서버 룰 기반 fallback 템플릿 사용 여부")
    private Boolean usedFallback;
}
