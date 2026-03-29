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
@Schema(description = "친구 요청 생성 요청")
public class FriendRequestCreateDTO {

    @Schema(example = "USER_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetUserId;
}
