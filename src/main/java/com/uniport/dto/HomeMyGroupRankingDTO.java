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
@Schema(description = "내 그룹 랭킹 요약")
public class HomeMyGroupRankingDTO {

    @Schema(example = "2", nullable = true)
    private Integer rank;

    @Schema(example = "7", nullable = true)
    private Long groupId;

    @Schema(example = "투자왕 그룹", nullable = true)
    private String groupName;
}
