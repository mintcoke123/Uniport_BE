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
@Schema(description = "친구 요청 생성 응답")
public class FriendRequestResponseDTO {

    @Schema(example = "REQ_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;

    @Schema(example = "USER_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetUserId;

    @Schema(example = "REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "2026-03-11T12:10:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
