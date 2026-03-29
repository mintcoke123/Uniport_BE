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
@Schema(description = "커뮤니티 댓글 작성 응답")
public class CommunityCommentMutationResponseDTO {

    @Schema(example = "COMMENT_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commentId;

    @Schema(example = "2026-03-11T08:25:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
