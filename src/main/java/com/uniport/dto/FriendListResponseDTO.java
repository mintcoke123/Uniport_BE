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
@Schema(description = "친구 목록 응답")
public class FriendListResponseDTO {

    @ArraySchema(schema = @Schema(implementation = FriendListItemDTO.class))
    private List<FriendListItemDTO> items;
}
