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
@Schema(description = "커뮤니티 게시글 요약")
public class CommunityPostSummaryDTO {

    @Schema(example = "POST_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postId;

    @Schema(example = "ACHIEVEMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    private CommunityAuthorDTO author;

    @Schema(example = "400일 연속 학습 달성!")
    private String title;

    @Schema(example = "오늘도 투자 공부 완료했습니다.")
    private String content;

    @Schema(example = "2984", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer likeCount;

    @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer commentCount;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean liked;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isMine;

    @Schema(example = "2026-03-11T08:20:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
