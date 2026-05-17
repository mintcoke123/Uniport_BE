package com.uniport.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FestivalSessionStartResponseDTO {
    private Long sessionId;
    private String displayName;
    private BigDecimal startCash;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String status;
    private Boolean canStart;
}
