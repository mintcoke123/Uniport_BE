package com.uniport.service.kisws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 한국시간 07:59:50에 KIS WebSocket 강제 재연결.
 * 야간 idle/timeout 후 아침 불안정 방지.
 */
@Component
public class KisWsDailyReconnectScheduler {

    private static final Logger log = LoggerFactory.getLogger(KisWsDailyReconnectScheduler.class);

    private final KisWsClient kisWsClient;

    public KisWsDailyReconnectScheduler(KisWsClient kisWsClient) {
        this.kisWsClient = kisWsClient;
    }

    @Scheduled(cron = "50 59 7 * * *", zone = "Asia/Seoul")
    public void dailyReconnect() {
        log.info("KIS WS daily reconnect triggered (07:59:50 KST)");
        kisWsClient.forceReconnect("daily 08:00 KST reconnect");
    }
}
