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
@Schema(description = "커뮤니티 신고 요청")
public class CommunityReportRequestDTO {

    @Schema(example = "SPAM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @Schema(example = "광고성 링크 반복 게시")
    private String detail;
}
