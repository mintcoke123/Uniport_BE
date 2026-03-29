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
@Schema(description = "커뮤니티 게시글 작성 요청")
public class CommunityPostCreateRequestDTO {

    @Schema(example = "GENERAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "레벨업 20 달성")
    private String title;

    @Schema(example = "꾸준히 공부 중")
    private String content;

    @Schema(example = "REPORT_301")
    private String analysisReportId;
}
