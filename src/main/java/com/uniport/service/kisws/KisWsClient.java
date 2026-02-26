package com.uniport.service.kisws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.service.KisApiService;
import com.uniport.websocket.PriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * KIS 실시간 WebSocket 연결 (Java 표준 java.net.http.WebSocket).
 * 구독은 KisWsSubscriptionManager를 통해 자동 요청됨 (하드코딩 없음).
 */
@Component
public class KisWsClient {

    private static final Logger log = LoggerFactory.getLogger(KisWsClient.class);

    @Value("${kis.api.use-mock:false}")
    private boolean useMock;

    private final KisApiService kisApiService;
    private final StockRealtimeCache stockRealtimeCache;
    private final PriceCache priceCache;
    private final KisWsSubscriptionManager kisWsSubscriptionManager;
    private final PriceBroadcaster priceBroadcaster;

    /** 연결된 WebSocket (onOpen에서 설정, onClose/send 실패 시 null) */
    private volatile WebSocket webSocketRef;

    /** sendSubscribe 동시 호출 방지 및 send 실패 시 ref 정리 */
    private final Object sendLock = new Object();

    /** 재연결 스케줄 중복 방지 */
    private volatile boolean reconnectScheduled;
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kis-ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    /** PINGPONG 로그 억제: 마지막 info 로그 시각 */
    private volatile long lastPongLogMillis;

    /** H0STCNT0 payload(^ 구분) 필드 순서: 명세 [실시간-003] Body 순서
     * [0]MKSC_SHRN_ISCD, [1]STCK_CNTG_HOUR, [2]STCK_PRPR, [3]PRDY_VRSS_SIGN, [4]PRDY_VRSS, [5]PRDY_CTRT, ... [13]ACML_VOL */
    private static final int IDX_STOCK_CODE = 0;
    private static final int IDX_CURRENT_PRICE = 2;   // STCK_PRPR
    private static final int IDX_CHANGE = 4;          // PRDY_VRSS (전일 대비)
    private static final int IDX_CHANGE_RATE = 5;     // PRDY_CTRT (전일 대비율)
    private static final int IDX_ACML_VOL = 13;      // ACML_VOL (누적 거래량)

    public KisWsClient(KisApiService kisApiService, StockRealtimeCache stockRealtimeCache,
                      PriceCache priceCache, @Lazy KisWsSubscriptionManager kisWsSubscriptionManager,
                      PriceBroadcaster priceBroadcaster) {
        this.kisApiService = kisApiService;
        this.stockRealtimeCache = stockRealtimeCache;
        this.priceCache = priceCache;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
        this.priceBroadcaster = priceBroadcaster;
    }

    @PostConstruct
    public void connect() {
        log.info("KIS WS connect start");
        if (!kisApiService.isKisConfigured()) {
            log.debug("KIS not configured, skipping WebSocket");
            return;
        }
        try {
            String base = useMock ? "ws://ops.koreainvestment.com:31000" : "ws://ops.koreainvestment.com:21000";
            URI uri = URI.create(base);

            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("KIS WS connect success");
                            webSocketRef = webSocket;
                            webSocket.request(1);
                            kisWsSubscriptionManager.onWsConnected();
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String text = data != null ? data.toString() : "";
                            boolean isPingPong = false;
                            boolean subscribeSuccess = false;
                            boolean isJson = text.trim().startsWith("{");
                            if (isJson) {
                                try {
                                    JsonNode root = new ObjectMapper().readTree(text);
                                    JsonNode header = root.path("header");
                                    if (!header.isMissingNode()) {
                                        String trId = header.path("tr_id").asText("");
                                        if ("PINGPONG".equals(trId)) {
                                            isPingPong = true;
                                        }
                                    }
                                    JsonNode body = root.path("body");
                                    if (!body.isMissingNode()) {
                                        String msg1 = body.path("msg1").asText("");
                                        if ("SUBSCRIBE SUCCESS".equals(msg1)) {
                                            subscribeSuccess = true;
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            } else if (text.startsWith("0|") && text.contains("H0STCNT0")) {
                                try {
                                    String[] recvstr = text.split("\\|", -1);
                                    if (recvstr.length >= 3 && "H0STCNT0".equals(recvstr[1])) {
                                        String payload = recvstr.length >= 4 ? recvstr[3] : recvstr[2];
                                        String[] fields = payload.split("\\^", -1);
                                        if (fields.length > IDX_CHANGE_RATE) {
                                            String stockCode = safeTrim(fields[IDX_STOCK_CODE]);
                                            BigDecimal currentPrice = parseBigDecimal(fields[IDX_CURRENT_PRICE]);
                                            BigDecimal change = parseBigDecimal(fields[IDX_CHANGE]);
                                            BigDecimal changeRate = parseBigDecimal(fields[IDX_CHANGE_RATE]);
                                            Long volume = fields.length > IDX_ACML_VOL ? parseLong(fields[IDX_ACML_VOL]) : 0L;
                                            if (stockCode != null && currentPrice != null) {
                                                long now = System.currentTimeMillis();
                                                PriceSnapshot snapshot = new PriceSnapshot(
                                                        currentPrice,
                                                        change != null ? change : BigDecimal.ZERO,
                                                        changeRate != null ? changeRate : BigDecimal.ZERO,
                                                        volume != null ? volume : 0L,
                                                        now);
                                                priceCache.put(stockCode, snapshot);
                                                priceBroadcaster.broadcast(stockCode, snapshot);
                                                RealtimeStock rt = new RealtimeStock(stockCode, currentPrice, change != null ? change : BigDecimal.ZERO, changeRate != null ? changeRate : BigDecimal.ZERO, volume != null ? volume : 0L, now);
                                                stockRealtimeCache.put(stockCode, rt);
                                                log.debug("실시간 캐시 갱신 stock={} price={} vol={}", stockCode, currentPrice, volume);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("KIS WS H0STCNT0 parse failed");
                                }
                            }
                            if (isPingPong) {
                                webSocket.sendText(text, true).whenComplete((w, ex) -> {
                                    if (ex == null) {
                                        long now = System.currentTimeMillis();
                                        if (now - lastPongLogMillis > 60_000) {
                                            log.info("KIS WS pong sent");
                                            lastPongLogMillis = now;
                                        } else {
                                            log.debug("KIS WS pong sent");
                                        }
                                    }
                                });
                            } else if (subscribeSuccess) {
                                log.debug("KIS WS SUBSCRIBE SUCCESS");
                            }
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            webSocketRef = null;
                            kisWsSubscriptionManager.onWsDisconnected();
                            log.info("KIS WS close statusCode={} reason={}", statusCode, reason);
                            scheduleReconnect();
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.warn("KIS WS error: {}", error != null ? error.toString() : "");
                            webSocketRef = null;
                            kisWsSubscriptionManager.onWsDisconnected();
                            scheduleReconnect();
                        }
                    })
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            log.warn("KIS WS buildAsync failed: {}", ex.toString());
                            webSocketRef = null;
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            log.warn("KIS WS error: {}", e.toString());
        }
    }

    /** 연결 여부. 구독 요청은 연결된 경우에만 유효. */
    public boolean isConnected() {
        return webSocketRef != null;
    }

    /**
     * 강제 재연결 (예: 매일 07:59:50 KST). 기존 scheduleReconnect 흐름 재사용.
     * ref가 있으면 abort 후 scheduleReconnect.
     */
    public void forceReconnect(String reason) {
        log.info("KIS WS force reconnect: {}", reason);
        WebSocket ws = webSocketRef;
        webSocketRef = null;
        if (ws != null) {
            try {
                ws.abort();
            } catch (Exception e) {
                log.debug("KIS WS abort: {}", e.getMessage());
            }
        }
        scheduleReconnect();
    }

    /**
     * Sends H0STCNT0 subscribe. Uses sendLock to serialize sends. On send failure (e.g. Output closed),
     * clears webSocketRef and schedules reconnect. KIS body.input JSON format.
     */
    public void sendSubscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        String code = stockCode.length() >= 6 ? stockCode : String.format("%6s", stockCode).replace(' ', '0');
        String approvalKey;
        try {
            approvalKey = kisApiService.getWebSocketApprovalKey();
        } catch (Exception e) {
            log.debug("KIS WS subscribe skipped (approval key): {}", e.getMessage());
            return;
        }
        String escaped = approvalKey.replace("\\", "\\\\").replace("\"", "\\\"");
        String subscribeJson = "{\"header\":{\"approval_key\":\"" + escaped
                + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"utf-8\"}"
                + ",\"body\":{\"input\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"" + code + "\"}}}";

        synchronized (sendLock) {
            WebSocket ws = webSocketRef;
            if (ws == null) {
                return;
            }
            try {
                ws.sendText(subscribeJson, true).whenComplete((w, ex) -> {
                    if (ex != null) {
                        log.warn("KIS WS subscribe send failed: {}", ex.toString());
                        webSocketRef = null;
                        scheduleReconnect();
                    } else {
                        log.debug("KIS WS subscribe sent: {}", code);
                    }
                });
            } catch (Exception e) {
                log.warn("KIS WS subscribe failed: {}", e.toString());
                webSocketRef = null;
                scheduleReconnect();
            }
        }
    }

    /** 지연 재연결 1회 스케줄. 중복 스케줄 방지. */
    private void scheduleReconnect() {
        if (!kisApiService.isKisConfigured()) {
            return;
        }
        synchronized (sendLock) {
            if (reconnectScheduled) {
                return;
            }
            reconnectScheduled = true;
        }
        reconnectExecutor.schedule(() -> {
            try {
                log.info("KIS WS reconnect attempt");
                connect();
            } finally {
                reconnectScheduled = false;
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        reconnectExecutor.shutdown();
        try {
            if (!reconnectExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                reconnectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Long.parseLong(s.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
