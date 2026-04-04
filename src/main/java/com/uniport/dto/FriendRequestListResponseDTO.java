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
@Schema(description = "친구 요청 목록 응답")
public class FriendRequestListResponseDTO {

    @ArraySchema(schema = @Schema(implementation = FriendRequestListItemDTO.class))
    private List<FriendRequestListItemDTO> items;
}
