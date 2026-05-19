package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetaIosTestFlightSyncResponseDTO {
    private int processed;
    private int added;
    private int pending;
    private int failed;
    private int skipped;
}
