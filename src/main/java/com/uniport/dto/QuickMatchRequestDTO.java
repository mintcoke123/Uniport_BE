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
@Schema(description = "Quick match request")
public class QuickMatchRequestDTO {

    @Schema(
            description = "Match mode",
            example = "RANDOM",
            allowableValues = {"RANDOM", "FRIEND", "SOLO"}
    )
    private String mode;

    @Schema(
            description = "Market type",
            example = "KR",
            allowableValues = {"KR", "US"}
    )
    private String marketType;

    @ArraySchema(schema = @Schema(description = "User ID to invite in FRIEND mode", example = "21"))
    private List<Long> inviteeUserIds;
}
