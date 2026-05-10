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
@Schema(description = "Invite users to matching room request")
public class MatchingRoomInviteUsersRequestDTO {

    @ArraySchema(schema = @Schema(description = "Invitee user ID", example = "21"))
    private List<Long> inviteeUserIds;
}
