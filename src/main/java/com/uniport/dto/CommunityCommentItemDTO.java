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
@Schema(description = "커뮤니티 댓글 아이템")
public class CommunityCommentItemDTO {

    @Schema(example = "COMMENT_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commentId;

    private CommunityAuthorDTO author;

    @Schema(example = "축하해요!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isMine;

    @Schema(example = "2026-03-11T08:25:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
