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
@Schema(description = "친구 초대 수락 응답")
public class FriendInviteAcceptResponseDTO {

    @Schema(example = "USER_1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String friendUserId;

    @Schema(example = "ACCEPTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
