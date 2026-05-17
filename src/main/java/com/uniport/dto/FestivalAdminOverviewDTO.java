package com.uniport.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FestivalAdminOverviewDTO {
    private Integer totalParticipants;
    private Integer completedParticipants;
    private Integer activeParticipants;
    private Integer qualifiedParticipants;
    private BigDecimal averageReturnRate;
    private BigDecimal bestReturnRate;
    private LocalDateTime lastCompletedAt;
    private List<FestivalAdminSessionItemDTO> sessions;
}
