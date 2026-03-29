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
@Schema(description = "커뮤니티 피드 목록 응답")
public class CommunityPostsResponseDTO {

    @ArraySchema(schema = @Schema(implementation = CommunityPostSummaryDTO.class))
    private List<CommunityPostSummaryDTO> items;

    @Schema(example = "POST_120")
    private String nextCursor;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasNext;
}
