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
@Schema(description = "커뮤니티 작성자 정보")
public class CommunityAuthorDTO {

    @Schema(example = "투자곰", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "https://example.com/profile.png")
    private String profileImageUrl;
}
