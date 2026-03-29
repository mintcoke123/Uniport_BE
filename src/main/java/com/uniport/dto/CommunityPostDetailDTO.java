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
@Schema(description = "커뮤니티 게시글 상세")
public class CommunityPostDetailDTO {

    @Schema(example = "POST_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postId;

    @Schema(example = "GENERAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    private CommunityAuthorDTO author;

    @Schema(example = "레벨업 20 달성")
    private String title;

    @Schema(example = "꾸준히 투자 공부 중입니다.")
    private String content;

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer likeCount;

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer commentCount;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean liked;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isMine;

    @Schema(example = "2026-03-11T08:20:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;

    @Schema(example = "2026-03-11T09:00:00Z")
    private String updatedAt;
}
