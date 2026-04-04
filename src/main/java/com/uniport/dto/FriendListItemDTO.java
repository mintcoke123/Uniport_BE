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
@Schema(description = "친구 목록 아이템")
public class FriendListItemDTO {

    @Schema(example = "USER_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Schema(example = "고윤서", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "https://cdn.example.com/friend-1.png")
    private String profileImageUrl;

    @Schema(example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @Schema(example = "균형잡힌 판다형")
    private String investmentProfileLabel;

    @Schema(example = "640")
    private Integer currentXp;

    @Schema(example = "1000")
    private Integer maxXp;

    @Schema(example = "친구")
    private String relationLabel;

    @Schema(example = "같이 공부하면 경험치가 2배!")
    private String description;
}
