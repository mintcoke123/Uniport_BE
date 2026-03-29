package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "친구 랭킹 섹션")
public class FriendRankingSectionDTO {

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer endDay;

    @ArraySchema(schema = @Schema(implementation = FriendRankingItemDTO.class))
    private List<FriendRankingItemDTO> items;
}
