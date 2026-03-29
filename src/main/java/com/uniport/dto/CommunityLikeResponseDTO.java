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
@Schema(description = "게시글 좋아요 응답")
public class CommunityLikeResponseDTO {

    @Schema(example = "POST_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postId;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean liked;

    @Schema(example = "13", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer likeCount;
}
