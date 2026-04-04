package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "온보딩 닉네임 저장 요청")
public class OnboardingNicknameUpdateRequestDTO {

    @Schema(example = "유니포트", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
}
