package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@Schema(description = "커뮤니티 댓글 목록 응답")
public class CommunityCommentsResponseDTO {

    @ArraySchema(schema = @Schema(implementation = CommunityCommentItemDTO.class))
    private List<CommunityCommentItemDTO> items;

    @Schema(example = "COMMENT_120")
    private String nextCursor;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasNext;
}
