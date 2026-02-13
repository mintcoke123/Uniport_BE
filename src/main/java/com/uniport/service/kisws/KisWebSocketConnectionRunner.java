package com.uniport.service.kisws;

import com.uniport.service.KisApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KIS 실시간 WebSocket 연결을 애플리케이션 기동 후 수립.
 * 연결 끊김 시 1~2회 재연결(짧은 backoff). 구독/메시지 파싱/프론트 중계는 하지 않음.
 */
@Component
public class KisWebSocketConnectionRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketConnectionRunner.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 2;
    private static final long BACKOFF_MS = 1_000L;

    private final KisApiService kisApiService;
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kis-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger reconnectCount = new AtomicInteger(0);

    public KisWebSocketConnectionRunner(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        connect();
    }

    private void connect() {
        if (!kisApiService.isKisConfigured()) {
            log.debug("KIS not configured, skipping WebSocket connection");
            return;
        }
        try {
            String approvalKey = kisApiService.getWebSocketApprovalKey();
            String baseUrl = kisApiService.getKisWebSocketBaseUrl();
            String uriStr = baseUrl + "?approval_key=" + java.net.URLEncoder.encode(approvalKey, StandardCharsets.UTF_8);
            URI uri = URI.create(uriStr);

            KisWebSocketHandler handler = new KisWebSocketHandler(this::scheduleReconnect);
            client.execute(handler, null, uri).whenComplete(
                    (session, ex) -> {
                        if (ex != null) {
                            log.warn("KIS WebSocket connection failed");
                        }
                    }
            );
        } catch (Exception e) {
            log.warn("KIS WebSocket connection failed");
        }
    }

    private void scheduleReconnect() {
        int attempt = reconnectCount.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.info("KIS WebSocket reconnect limit reached");
            return;
        }
        long delayMs = attempt * BACKOFF_MS;
        log.info("KIS WebSocket reconnecting ({}/{}), backoff {}ms", attempt, MAX_RECONNECT_ATTEMPTS, delayMs);
        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }
}
