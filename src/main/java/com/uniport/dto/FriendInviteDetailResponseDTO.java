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
@Schema(description = "친구 초대 상세 응답")
public class FriendInviteDetailResponseDTO {

    @Schema(example = "abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteCode;

    @Schema(example = "USER_1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviterUserId;

    @Schema(example = "곽건", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviterNickname;

    @Schema(example = "https://example.com/profile.png")
    private String inviterProfileImageUrl;

    @Schema(example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "2026-05-16T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expiresAt;
}
