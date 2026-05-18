package com.uniport.scheduler;

import com.uniport.service.CompetitionSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompetitionSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompetitionSettlementScheduler.class);

    private final CompetitionSettlementService settlementService;

    public CompetitionSettlementScheduler(CompetitionSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Scheduled(fixedDelayString = "${competition.settlement.job.fixed-delay-ms:300000}")
    public void run() {
        try {
            int settled = settlementService.settleExpiredCompetitions();
            if (settled > 0) {
                log.info("[competition-settlement] settled={}", settled);
            }
        } catch (Exception e) {
            log.warn("[competition-settlement] job failed: {}", e.getMessage());
        }
    }
}
