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
@Schema(description = "친구 랭킹 아이템")
public class FriendRankingItemDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rank;

    @Schema(example = "USER_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Schema(example = "김지수", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "https://cdn.example.com/user1.png")
    private String profileImageUrl;

    @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @Schema(example = "12450", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer xp;

    @Schema(example = "2")
    private Integer rankChange;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alreadyInvited;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alreadyMatched;
}
