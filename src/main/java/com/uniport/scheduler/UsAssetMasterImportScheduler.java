package com.uniport.scheduler;

import com.uniport.service.importer.ImportResult;
import com.uniport.service.importer.UsAssetMasterImporterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsAssetMasterImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsAssetMasterImportScheduler.class);
    private static final long ADVISORY_LOCK_KEY = 912345679L;

    @Value("${asset.master.us.import.enabled:false}")
    private boolean enabled;

    private final UsAssetMasterImporterService usAssetMasterImporterService;
    private final JdbcTemplate jdbcTemplate;

    public UsAssetMasterImportScheduler(UsAssetMasterImporterService usAssetMasterImporterService,
                                        JdbcTemplate jdbcTemplate) {
        this.usAssetMasterImporterService = usAssetMasterImporterService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${asset.master.us.import.cron:0 45 7 * * *}", zone = "${asset.master.us.import.zone:Asia/Seoul}")
    @Transactional
    public void run() {
        if (!enabled) {
            return;
        }
        boolean locked;
        try {
            locked = PostgresAdvisoryLock.tryLock(jdbcTemplate, ADVISORY_LOCK_KEY);
        } catch (Exception e) {
            log.warn("[us-asset-master-import-scheduler] advisory lock 시도 실패, 이번 실행 스킵", e);
            return;
        }
        if (!locked) {
            log.info("[us-asset-master-import-scheduler] advisory lock 미획득, 이번 실행 스킵");
            return;
        }
        try {
            long startMs = System.currentTimeMillis();
            log.info("[us-asset-master-import-scheduler] 시작");
            ImportResult result = usAssetMasterImporterService.importAll();
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[us-asset-master-import-scheduler] 완료: inserted={}, updated={}, skipped={}, 소요={}ms",
                    result.getInserted(), result.getUpdated(), result.getSkipped(), elapsed);
        } catch (Exception e) {
            log.error("[us-asset-master-import-scheduler] importAll 실패 (예외 전파하지 않음)", e);
        } finally {
            PostgresAdvisoryLock.unlock(jdbcTemplate, ADVISORY_LOCK_KEY);
        }
    }
}
