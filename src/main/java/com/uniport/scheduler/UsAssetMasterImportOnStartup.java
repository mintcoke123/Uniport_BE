package com.uniport.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(101)
public class UsAssetMasterImportOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsAssetMasterImportOnStartup.class);

    private final UsAssetMasterImportScheduler scheduler;
    private final boolean startupEnabled;

    public UsAssetMasterImportOnStartup(
            UsAssetMasterImportScheduler scheduler,
            @Value("${asset.master.us.import.on-startup.enabled:true}") boolean startupEnabled
    ) {
        this.scheduler = scheduler;
        this.startupEnabled = startupEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!startupEnabled) {
            log.info("[us-asset-master-import] startup import disabled");
            return;
        }
        log.info("[us-asset-master-import] 서버 기동 시 1회 실행");
        scheduler.run();
    }
}
