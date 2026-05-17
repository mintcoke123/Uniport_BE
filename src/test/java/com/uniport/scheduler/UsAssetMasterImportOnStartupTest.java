package com.uniport.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UsAssetMasterImportOnStartupTest {

    @Test
    void run_triggersUsAssetMasterImportSchedulerOnce() {
        UsAssetMasterImportScheduler scheduler = mock(UsAssetMasterImportScheduler.class);
        UsAssetMasterImportOnStartup runner = new UsAssetMasterImportOnStartup(scheduler, true);

        runner.run(mock(ApplicationArguments.class));

        verify(scheduler).run();
    }

    @Test
    void run_skipsSchedulerWhenStartupImportDisabled() {
        UsAssetMasterImportScheduler scheduler = mock(UsAssetMasterImportScheduler.class);
        UsAssetMasterImportOnStartup runner = new UsAssetMasterImportOnStartup(scheduler, false);

        runner.run(mock(ApplicationArguments.class));

        verify(scheduler, never()).run();
    }
}
