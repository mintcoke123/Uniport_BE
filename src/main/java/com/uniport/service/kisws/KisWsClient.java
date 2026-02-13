package com.uniport.service.kisws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.service.KisApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    /** 연결된 WebSocket (onOpen에서 설정, onClose에서 null) */
    private volatile WebSocket webSocketRef;

    /** PINGPONG 로그 억제: 마지막 info 로그 시각 */
    private volatile long lastPongLogMillis;

    /** H0STCNT0 payload(^ 구분) 필드 순서: [0]MKSC_SHRN_ISCD, [1]tradeTime(미사용), [2]STCK_PRPR, [3]PRDY_VRSS, [4]PRDY_CTRT, [5]ACML_VOL */
    private static final int IDX_STOCK_CODE = 0;
    private static final int IDX_CURRENT_PRICE = 2;
    private static final int IDX_CHANGE = 3;
    private static final int IDX_CHANGE_RATE = 4;
    private static final int IDX_VOLUME = 5;

    public KisWsClient(KisApiService kisApiService, StockRealtimeCache stockRealtimeCache,
                      PriceCache priceCache, @Lazy KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.kisApiService = kisApiService;
        this.stockRealtimeCache = stockRealtimeCache;
        this.priceCache = priceCache;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
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
                                        if (fields.length > IDX_VOLUME) {
                                            String stockCode = safeTrim(fields[IDX_STOCK_CODE]);
                                            BigDecimal currentPrice = parseBigDecimal(fields[IDX_CURRENT_PRICE]);
                                            BigDecimal change = parseBigDecimal(fields[IDX_CHANGE]);
                                            BigDecimal changeRate = parseBigDecimal(fields[IDX_CHANGE_RATE]);
                                            Long volume = parseLong(fields[IDX_VOLUME]);
                                            if (stockCode != null && currentPrice != null) {
                                                long now = System.currentTimeMillis();
                                                PriceSnapshot snapshot = new PriceSnapshot(
                                                        currentPrice,
                                                        change != null ? change : BigDecimal.ZERO,
                                                        changeRate != null ? changeRate : BigDecimal.ZERO,
                                                        volume != null ? volume : 0L,
                                                        now);
                                                priceCache.put(stockCode, snapshot);
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
                            kisWsSubscriptionManager.clearSubscribedCodes();
                            log.info("KIS WS close statusCode={} reason={}", statusCode, reason);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.warn("KIS WS error: {}", error != null ? error.toString() : "");
                        }
                    })
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            log.warn("KIS WS buildAsync failed: {}", ex.toString());
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
     * H0STCNT0 실시간 체결 구독 전송. 연결된 경우에만 전송.
     * KIS 문서: body는 반드시 {"input": {...}} 형태로 감싼다.
     * tr_key에 6자리 종목코드 사용.
     */
    public void sendSubscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank() || webSocketRef == null) {
            return;
        }
        String code = stockCode.length() >= 6 ? stockCode : String.format("%6s", stockCode).replace(' ', '0');
        try {
            String approvalKey = kisApiService.getWebSocketApprovalKey();
            String escaped = approvalKey.replace("\\", "\\\\").replace("\"", "\\\"");
            String subscribeJson = "{\"header\":{\"approval_key\":\"" + escaped
                    + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"utf-8\"}"
                    + ",\"body\":{\"input\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"" + code + "\"}}}";
            webSocketRef.sendText(subscribeJson, true).whenComplete((w, ex) -> {
                if (ex != null) {
                    log.warn("KIS WS subscribe send failed: {}", ex.toString());
                } else {
                    log.debug("KIS WS subscribe sent: {}", code);
                }
            });
        } catch (Exception e) {
            log.warn("KIS WS subscribe failed");
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
