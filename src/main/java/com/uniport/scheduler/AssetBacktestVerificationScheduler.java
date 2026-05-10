package com.uniport.scheduler;

import com.uniport.service.AssetBacktestVerificationService;
import com.uniport.service.importer.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssetBacktestVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AssetBacktestVerificationScheduler.class);
    private static final long ADVISORY_LOCK_KEY = 912345680L;

    @Value("${asset.backtest-verification.enabled:false}")
    private boolean enabled;

    @Value("${asset.backtest-verification.batch-size:200}")
    private int batchSize;

    private final AssetBacktestVerificationService verificationService;
    private final JdbcTemplate jdbcTemplate;

    public AssetBacktestVerificationScheduler(AssetBacktestVerificationService verificationService,
                                              JdbcTemplate jdbcTemplate) {
        this.verificationService = verificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${asset.backtest-verification.cron:0 30 8 * * *}",
            zone = "${asset.backtest-verification.zone:Asia/Seoul}")
    @Transactional
    public void run() {
        if (!enabled) {
            return;
        }
        boolean locked;
        try {
            locked = PostgresAdvisoryLock.tryLock(jdbcTemplate, ADVISORY_LOCK_KEY);
        } catch (Exception e) {
            log.warn("[asset-backtest-verification-scheduler] advisory lock 시도 실패, 이번 실행 스킵", e);
            return;
        }
        if (!locked) {
            log.info("[asset-backtest-verification-scheduler] advisory lock 미획득, 이번 실행 스킵");
            return;
        }
        try {
            long startMs = System.currentTimeMillis();
            ImportResult result = verificationService.verifyActiveAssets(batchSize);
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[asset-backtest-verification-scheduler] 완료: enabled={}, disabled={}, 소요={}ms",
                    result.getUpdated(), result.getSkipped(), elapsed);
        } catch (Exception e) {
            log.error("[asset-backtest-verification-scheduler] 검증 실패 (예외 전파하지 않음)", e);
        } finally {
            PostgresAdvisoryLock.unlock(jdbcTemplate, ADVISORY_LOCK_KEY);
        }
    }
}
