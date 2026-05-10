package com.uniport.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UsAssetMasterImportOnStartupTest {

    @Test
    void run_triggersUsAssetMasterImportSchedulerOnce() {
        UsAssetMasterImportScheduler scheduler = mock(UsAssetMasterImportScheduler.class);
        UsAssetMasterImportOnStartup runner = new UsAssetMasterImportOnStartup(scheduler);

        runner.run(mock(ApplicationArguments.class));

        verify(scheduler).run();
    }
}
