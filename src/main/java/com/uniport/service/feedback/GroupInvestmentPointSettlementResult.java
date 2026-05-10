package com.uniport.service.feedback;

public record GroupInvestmentPointSettlementResult(
        int settledMemberCount,
        int skippedMemberCount,
        int totalSettledPoint
) {
}
