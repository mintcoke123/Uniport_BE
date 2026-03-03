package com.uniport.scheduler;

import com.uniport.service.importer.ImportResult;
import com.uniport.service.importer.StockMasterImporterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매일 지정 시각(KST 07:30 기본)에 종목 마스터 자동 갱신.
 * stock.master.import.enabled=true 일 때만 실행. Postgres advisory lock으로 다중 인스턴스 중 1개만 실행.
 * 예외는 로그만 남기고 전파하지 않음.
 */
@Component
public class StockMasterImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockMasterImportScheduler.class);
    private static final long ADVISORY_LOCK_KEY = 912345678L;

    @Value("${stock.master.import.enabled:false}")
    private boolean enabled;

    private final StockMasterImporterService stockMasterImporterService;
    private final JdbcTemplate jdbcTemplate;

    public StockMasterImportScheduler(StockMasterImporterService stockMasterImporterService,
                                      JdbcTemplate jdbcTemplate) {
        this.stockMasterImporterService = stockMasterImporterService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${stock.master.import.cron:0 22 9 * * *}", zone = "${stock.master.import.zone:Asia/Seoul}")
    @Transactional
    public void run() {
        if (!enabled) {
            return;
        }
        boolean locked = false;
        try {
            locked = PostgresAdvisoryLock.tryLock(jdbcTemplate, ADVISORY_LOCK_KEY);
        } catch (Exception e) {
            log.warn("[stock-master-import-scheduler] advisory lock 시도 실패 (예: 비-Postgres DB), 이번 실행 스킵", e);
            return;
        }
        if (!locked) {
            log.info("[stock-master-import-scheduler] advisory lock 미획득, 이번 실행 스킵 (다른 인스턴스에서 실행 중일 수 있음)");
            return;
        }
        try {
            long startMs = System.currentTimeMillis();
            log.info("[stock-master-import-scheduler] 시작");
            ImportResult result = stockMasterImporterService.importAll();
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[stock-master-import-scheduler] 완료: inserted={}, updated={}, skipped={}, 소요={}ms",
                    result.getInserted(), result.getUpdated(), result.getSkipped(), elapsed);
        } catch (Exception e) {
            log.error("[stock-master-import-scheduler] importAll 실패 (예외 전파하지 않음)", e);
        } finally {
            PostgresAdvisoryLock.unlock(jdbcTemplate, ADVISORY_LOCK_KEY);
        }
    }
}
