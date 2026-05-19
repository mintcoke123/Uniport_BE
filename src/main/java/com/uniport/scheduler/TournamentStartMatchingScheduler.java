package com.uniport.scheduler;

import com.uniport.service.TournamentStartMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TournamentStartMatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TournamentStartMatchingScheduler.class);

    private final TournamentStartMatchingService tournamentStartMatchingService;

    public TournamentStartMatchingScheduler(TournamentStartMatchingService tournamentStartMatchingService) {
        this.tournamentStartMatchingService = tournamentStartMatchingService;
    }

    @Scheduled(fixedDelayString = "${competition.start-matching.job.fixed-delay-ms:60000}")
    public void run() {
        try {
            int completed = tournamentStartMatchingService.processDueTournaments();
            if (completed > 0) {
                log.info("[tournament-start-matching] completed={}", completed);
            }
        } catch (Exception e) {
            log.warn("[tournament-start-matching] job failed: {}", e.getMessage());
        }
    }
}
