package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Quick match response")
public class QuickMatchResponseDTO {

    @Schema(description = "Match mode", example = "RANDOM")
    private String mode;

    @Schema(description = "Result message", example = "Joined an existing random match room.")
    private String message;

    @Schema(
            description = "Matched or newly created room payload. The current implementation returns an object map.",
            type = "object",
            nullable = true
    )
    private Object room;

    @Schema(
            description = "Detailed room payload. The current implementation returns an object map.",
            type = "object",
            nullable = true
    )
    private Object detail;

    @Schema(description = "Assigned team ID when the room auto-starts", example = "team-12", nullable = true)
    private String teamId;

    @Schema(description = "Competition ID when the room auto-starts", example = "1", nullable = true)
    private Integer competitionId;
}
