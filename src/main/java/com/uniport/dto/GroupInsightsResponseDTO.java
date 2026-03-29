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
@Schema(description = "상위 그룹 인사이트 응답")
public class GroupInsightsResponseDTO {

    @ArraySchema(schema = @Schema(implementation = GroupInsightConsensusDTO.class))
    private List<GroupInsightConsensusDTO> topConsensus;

    @Schema(implementation = TopGroupInsightDTO.class)
    private TopGroupInsightDTO topGroup;
}
