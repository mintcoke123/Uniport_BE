package com.uniport.scheduler;

import com.uniport.service.BetaIosApplicationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BetaIosTestFlightGroupSyncSchedulerTest {

    @Test
    void syncPendingInternalTestersOnStartupDelegatesToService() {
        BetaIosApplicationService service = mock(BetaIosApplicationService.class);
        BetaIosTestFlightGroupSyncScheduler scheduler = new BetaIosTestFlightGroupSyncScheduler(service);

        scheduler.syncPendingInternalTestersOnStartup();

        verify(service).syncPendingInternalTesters();
    }
}
