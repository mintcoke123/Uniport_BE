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
@Schema(description = "온보딩 닉네임 저장 응답")
public class OnboardingNicknameUpdateResponseDTO {

    @Schema(example = "유니포트", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean updated;
}
