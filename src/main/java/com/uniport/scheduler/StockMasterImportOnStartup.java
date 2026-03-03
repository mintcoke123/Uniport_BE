package com.uniport.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 서버 기동 직후 stock_master 임포트 1회 실행.
 * stock.master.import.enabled=true 일 때만 실행. 스케줄러와 동일한 run() 호출.
 */
@Component
@Order(100)
public class StockMasterImportOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockMasterImportOnStartup.class);

    private final StockMasterImportScheduler scheduler;

    public StockMasterImportOnStartup(StockMasterImportScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[stock-master-import] 서버 기동 시 1회 실행");
        scheduler.run();
    }
}
