package com.uniport.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(101)
public class UsAssetMasterImportOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsAssetMasterImportOnStartup.class);

    private final UsAssetMasterImportScheduler scheduler;

    public UsAssetMasterImportOnStartup(UsAssetMasterImportScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[us-asset-master-import] 서버 기동 시 1회 실행");
        scheduler.run();
    }
}
