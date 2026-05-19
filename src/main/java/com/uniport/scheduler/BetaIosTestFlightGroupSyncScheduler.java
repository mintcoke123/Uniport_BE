package com.uniport.scheduler;

import com.uniport.service.BetaIosApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BetaIosTestFlightGroupSyncScheduler {

    private final BetaIosApplicationService betaIosApplicationService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncPendingInternalTestersOnStartup() {
        syncPendingInternalTesters();
    }

    @Scheduled(fixedDelayString = "${app.beta.ios.app-store-connect.group-sync-fixed-delay-ms:60000}")
    public void syncPendingInternalTesters() {
        try {
            betaIosApplicationService.syncPendingInternalTesters();
        } catch (RuntimeException e) {
            log.warn("Failed to sync iOS beta applicants into TestFlight internal group", e);
        }
    }
}
