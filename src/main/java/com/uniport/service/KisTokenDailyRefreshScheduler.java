package com.uniport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 한국시간 08:00에 KIS OAuth2 접근 토큰을 재발급.
 * 캐시 무효화 후 즉시 새 토큰을 발급해 두어, 당일 API 호출이 갱신된 토큰을 사용하도록 함.
 */
@Component
public class KisTokenDailyRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(KisTokenDailyRefreshScheduler.class);

    private final KisApiService kisApiService;

    public KisTokenDailyRefreshScheduler(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void dailyTokenRefresh() {
        if (!kisApiService.isKisConfigured()) {
            log.debug("KIS token daily refresh skipped (KIS not configured)");
            return;
        }
        log.info("KIS access token daily refresh triggered (08:00 KST)");
        kisApiService.refreshTokenAt8am();
    }
}
