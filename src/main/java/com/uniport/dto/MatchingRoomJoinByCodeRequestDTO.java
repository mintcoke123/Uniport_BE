package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Join matching room by invite code request")
public class MatchingRoomJoinByCodeRequestDTO {

    @Schema(description = "Invite code", example = "abc12345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteCode;
}
