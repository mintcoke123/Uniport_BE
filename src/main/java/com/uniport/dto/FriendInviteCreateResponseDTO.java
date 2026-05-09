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
@Schema(description = "친구 초대 생성 응답")
public class FriendInviteCreateResponseDTO {

    @Schema(example = "abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteCode;

    @Schema(example = "https://uniportbe-production.up.railway.app/friend-invite?inviteCode=abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteUrl;

    @Schema(example = "2026-05-16T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expiresAt;
}
