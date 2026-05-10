package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Matching room creation request")
public class MatchingRoomCreateRequestDTO {

    @Schema(description = "Room name", example = "Random Match Room")
    private String name;

    @Schema(description = "Room visibility", example = "PUBLIC", allowableValues = {"PUBLIC", "PRIVATE"})
    private String visibility;

    @Schema(description = "Maximum room capacity", example = "3")
    private Integer capacity;

    @Schema(description = "Match type", example = "RANDOM", allowableValues = {"RANDOM", "FRIEND"})
    private String matchType;

    @Schema(description = "Market type", example = "KR", allowableValues = {"KR", "US"})
    private String marketType;

    @ArraySchema(schema = @Schema(description = "Invitee user ID", example = "21"))
    private List<Long> inviteeUserIds;
}
