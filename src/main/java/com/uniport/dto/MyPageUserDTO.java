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
@Schema(description = "마이페이지 사용자 정보")
public class MyPageUserDTO {

    @Schema(example = "박세종", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(example = "https://cdn.example.com/profile.png")
    private String profileImageUrl;

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @Schema(example = "균형 투자형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String investmentMbti;

    @Schema(example = "균형 잡힌 판다", requiredMode = Schema.RequiredMode.REQUIRED)
    private String character;
}
