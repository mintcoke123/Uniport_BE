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
@Schema(description = "커뮤니티 댓글 작성 요청")
public class CommunityCommentCreateRequestDTO {

    @Schema(example = "축하해요!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
