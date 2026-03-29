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
@Schema(description = "친구 초대용 사용자 검색 결과")
public class UserSearchItemDTO {

    @Schema(example = "21", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "22001234", nullable = true)
    private String studentId;

    @Schema(example = "https://example.com/profile-21.png", nullable = true)
    private String profileImageUrl;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alreadyInvited;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alreadyMatched;
}
