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
@Schema(description = "커뮤니티 게시글 작성/수정 응답")
public class CommunityPostMutationResponseDTO {

    @Schema(example = "POST_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postId;

    @Schema(example = "2026-03-11T10:30:00Z")
    private String createdAt;

    @Schema(example = "2026-03-11T10:40:00Z")
    private String updatedAt;
}
