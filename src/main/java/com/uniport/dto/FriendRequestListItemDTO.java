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
@Schema(description = "친구 요청 목록 아이템")
public class FriendRequestListItemDTO {

    @Schema(example = "REQ_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;

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

    @Schema(example = "1시간 전")
    private String requestedAgoLabel;

    @Schema(example = "REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
