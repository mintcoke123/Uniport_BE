package com.uniport.service.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * importer 프로필에서만 활성화. 국내/미국 종목 마스터 1회 적재 후 프로세스 종료(운영 수동 실행용).
 * 기본 프로필에서는 빈이 등록되지 않아 자동 실행되지 않음.
 * 종료 시 SpringApplication.exit() 후 System.exit()로 프로세스 종료.
 */
@Component
@Profile("importer")
@Order(Integer.MIN_VALUE)
public class StockMasterImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockMasterImportRunner.class);

    private final AssetMasterImportService assetMasterImportService;
    private final ConfigurableApplicationContext context;

    public StockMasterImportRunner(AssetMasterImportService assetMasterImportService,
                                  ConfigurableApplicationContext context) {
        this.assetMasterImportService = assetMasterImportService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = runImportAndReturnExitCode();
        int code = SpringApplication.exit(context, () -> exitCode);
        System.exit(code);
    }

    private int runImportAndReturnExitCode() {
        try {
            AssetMasterImportService.CombinedImportResult result = assetMasterImportService.importAll();
            ImportResult total = result.total();
            log.info("[asset-master-import] 완료: domestic(inserted={}, updated={}, skipped={}), " +
                            "us(inserted={}, updated={}, skipped={}), total(inserted={}, updated={}, skipped={})",
                    result.domestic().getInserted(), result.domestic().getUpdated(), result.domestic().getSkipped(),
                    result.us().getInserted(), result.us().getUpdated(), result.us().getSkipped(),
                    total.getInserted(), total.getUpdated(), total.getSkipped());
            return 0;
        } catch (Exception e) {
            log.error("[asset-master-import] 실패. exit(1)", e);
            return 1;
        }
    }
}
