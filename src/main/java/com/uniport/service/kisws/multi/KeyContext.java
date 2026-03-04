package com.uniport.service.kisws.multi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.service.kisws.PriceCache;
import com.uniport.service.kisws.PriceSnapshot;
import com.uniport.service.kisws.RealtimeStock;
import com.uniport.service.kisws.StockRealtimeCache;
import com.uniport.websocket.PriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키 1개당 WebSocket 1개. 구독/펜딩은 KeyContext 내부에서만 관리.
 * PriceCache, PriceBroadcaster, StockRealtimeCache 업데이트 로직은 기존 KisWsClient와 동일.
 * KIS 공지: 1세션당 실시간 등록 41건(체결가+호가+예상체결+체결통보 등 합산) 제한.
 */
public class KeyContext {

    /** KIS 공지: 1세션당 41건. 방어적으로 40으로 제한해 여유 확보. */
    public static final int MAX_SUBSCRIPTIONS_PER_SESSION = 40;

    private static final Logger log = LoggerFactory.getLogger(KeyContext.class);
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private static final int IDX_STOCK_CODE = 0;
    private static final int IDX_CURRENT_PRICE = 2;
    private static final int IDX_CHANGE = 4;
    private static final int IDX_CHANGE_RATE = 5;
    private static final int IDX_ACML_VOL = 13;

    private final String keyId;
    private final ApprovalKeyProvider approvalKeyProvider;
    private final StockRealtimeCache stockRealtimeCache;
    private final PriceCache priceCache;
    private final PriceBroadcaster priceBroadcaster;
    private final boolean useMock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyCircuitBreaker circuitBreaker;
    private final TokenBucketLimiter wsSubscribeLimiter;

    private volatile WebSocket webSocketRef;
    private final Object sendLock = new Object();
    private volatile boolean reconnectScheduled;
    private volatile long lastPongLogMillis;
    private volatile long lastMessageAtMillis;
    private volatile long lastConnectedAtMillis;

    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingCodes = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService reconnectExecutor;

    public KeyContext(String keyId,
                      ApprovalKeyProvider approvalKeyProvider,
                      StockRealtimeCache stockRealtimeCache,
                      PriceCache priceCache,
                      PriceBroadcaster priceBroadcaster,
                      boolean useMock,
                      KeyCircuitBreaker circuitBreaker,
                      TokenBucketLimiter wsSubscribeLimiter) {
        this.keyId = keyId;
        this.approvalKeyProvider = approvalKeyProvider;
        this.stockRealtimeCache = stockRealtimeCache;
        this.priceCache = priceCache;
        this.priceBroadcaster = priceBroadcaster;
        this.useMock = useMock;
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new KeyCircuitBreaker(keyId);
        this.wsSubscribeLimiter = wsSubscribeLimiter != null ? wsSubscribeLimiter : new TokenBucketLimiter(50, 20.0);
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kis-ws-reconnect-" + keyId);
            t.setDaemon(true);
            return t;
        });
    }

    public String getKeyId() {
        return keyId;
    }

    public KeyHealth getHealth() {
        return circuitBreaker.health();
    }

    /** 연결됐고 회로 차단기가 열려 있지 않을 때만 true */
    public boolean isAvailable() {
        return isConnected() && circuitBreaker.isAvailable();
    }

    public Set<String> snapshotSubscribedCodes() {
        return new HashSet<>(subscribedCodes);
    }

    /** 현재 구독 수. 세션당 40건 제한 판단용. */
    public int getSubscribedCount() {
        return subscribedCodes.size();
    }

    /** 이 세션에 구독 1건 더 받을 수 있는지(40 미만). */
    public boolean canAcceptMore() {
        return subscribedCodes.size() < MAX_SUBSCRIPTIONS_PER_SESSION;
    }

    /** 해당 종목을 이 세션에서 구독 중인지. */
    public boolean hasSubscription(String stockCode) {
        return stockCode != null && subscribedCodes.contains(stockCode);
    }

    /** 구독 완료 또는 대기(pending) 중이면 true. 중복 구독 방지용. */
    public boolean hasSubscriptionOrPending(String stockCode) {
        return stockCode != null && (subscribedCodes.contains(stockCode) || pendingCodes.contains(stockCode));
    }

    public void markFailure(String reason) {
        circuitBreaker.onFailure(reason);
    }

    public void markSuccess() {
        circuitBreaker.onSuccess();
    }

    public void connect() {
        if (!approvalKeyProvider.isKeyConfigured(keyId)) {
            log.debug("KIS key not configured keyId={}, skipping WebSocket", keyId);
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
                            log.info("KIS WS connect success keyId={}", keyId);
                            webSocketRef = webSocket;
                            lastConnectedAtMillis = System.currentTimeMillis();
                            markSuccess();
                            webSocket.request(1);
                            drainPending();
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String text = data != null ? data.toString() : "";
                            boolean isPingPong = false;
                            boolean subscribeSuccess = false;
                            boolean isJson = text.trim().startsWith("{");
                            if (isJson) {
                                try {
                                    JsonNode root = objectMapper.readTree(text);
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
                                            String stockCode = safeTrim(getField(fields, IDX_STOCK_CODE));
                                            BigDecimal currentPrice = parseBigDecimal(getField(fields, IDX_CURRENT_PRICE));
                                            BigDecimal change = parseBigDecimal(getField(fields, IDX_CHANGE));
                                            BigDecimal changeRate = parseBigDecimal(getField(fields, IDX_CHANGE_RATE));
                                            Long volume = parseLong(getField(fields, IDX_ACML_VOL));
                                            if (stockCode != null && currentPrice != null) {
                                                long now = System.currentTimeMillis();
                                                lastMessageAtMillis = now;
                                                markSuccess();
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
                                                log.debug("실시간 캐시 갱신 keyId={} stock={} price={} vol={}", keyId, stockCode, currentPrice, volume);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("KIS WS H0STCNT0 parse failed keyId={} reason={} sample={}", keyId, e.getMessage(), text.length() > 200 ? text.substring(0, 200) + "..." : text);
                                    } else {
                                        log.warn("KIS WS H0STCNT0 parse failed keyId={} reason={}", keyId, e.getMessage());
                                    }
                                }
                            }
                            if (isPingPong) {
                                webSocket.sendText(text, true).whenComplete((w, ex) -> {
                                    if (ex == null) {
                                        long now = System.currentTimeMillis();
                                        if (now - lastPongLogMillis > 60_000) {
                                            log.info("KIS WS pong sent keyId={}", keyId);
                                            lastPongLogMillis = now;
                                        } else {
                                            log.debug("KIS WS pong sent keyId={}", keyId);
                                        }
                                    }
                                });
                            } else if (subscribeSuccess) {
                                log.debug("KIS WS SUBSCRIBE SUCCESS keyId={}", keyId);
                            }
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            webSocketRef = null;
                            markFailure("close");
                            onDisconnected();
                            log.info("KIS WS close keyId={} statusCode={} reason={}", keyId, statusCode, reason);
                            scheduleReconnect();
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.warn("KIS WS error keyId={}: {}", keyId, error != null ? error.toString() : "");
                            webSocketRef = null;
                            markFailure("error");
                            onDisconnected();
                            scheduleReconnect();
                        }
                    })
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            log.warn("KIS WS buildAsync failed keyId={}: {}", keyId, ex.toString());
                            webSocketRef = null;
                            markFailure("build");
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            log.warn("KIS WS error keyId={}: {}", keyId, e.toString());
            markFailure("connect");
        }
    }

    public boolean isConnected() {
        return webSocketRef != null;
    }

    public void forceReconnect(String reason) {
        log.info("KIS WS force reconnect keyId={}: {}", keyId, reason);
        WebSocket ws = webSocketRef;
        webSocketRef = null;
        if (ws != null) {
            try {
                ws.abort();
            } catch (Exception e) {
                log.debug("KIS WS abort keyId={}: {}", keyId, e.getMessage());
            }
        }
        onDisconnected();
        scheduleReconnect();
    }

    /**
     * 이미 구독 중이면 skip. 연결됐으면 바로 전송 후 subscribed에 추가, 아니면 pending에 추가.
     * stockCode는 호출부(KeyPool)에서 이미 정규화된 값만 전달된다. 여기서는 재정규화하지 않음.
     */
    public void ensureSubscribed(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        if (subscribedCodes.contains(stockCode)) {
            return;
        }
        if (!canAcceptMore()) {
            return;
        }
        if (isConnected()) {
            sendSubscribe(stockCode);
            subscribedCodes.add(stockCode);
            log.debug("KIS WS subscribe requested keyId={} code={}", keyId, stockCode);
        } else {
            pendingCodes.add(stockCode);
            log.debug("KIS WS subscribe pending (not connected) keyId={} code={}", keyId, stockCode);
        }
    }

    /**
     * stockCode는 이미 정규화된 값만 전달됨. 재정규화하지 않음.
     * 키별 WS subscribe rate 제한 적용.
     */
    public void sendSubscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        if (!wsSubscribeLimiter.tryAcquire()) {
            log.debug("KIS WS subscribe rate limited keyId={} code={}", keyId, stockCode);
            return;
        }
        String approvalKey;
        try {
            approvalKey = approvalKeyProvider.getApprovalKey(keyId);
        } catch (Exception e) {
            log.debug("KIS WS subscribe skipped (approval key) keyId={}: {}", keyId, e.getMessage());
            return;
        }
        String escaped = approvalKey.replace("\\", "\\\\").replace("\"", "\\\"");
        String subscribeJson = "{\"header\":{\"approval_key\":\"" + escaped
                + "\",\"custtype\":\"P\",\"tr_type\":\"1\",\"content-type\":\"utf-8\"}"
                + ",\"body\":{\"input\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"" + stockCode + "\"}}}";

        synchronized (sendLock) {
            WebSocket ws = webSocketRef;
            if (ws == null) {
                return;
            }
            try {
                ws.sendText(subscribeJson, true).whenComplete((w, ex) -> {
                    if (ex != null) {
                        log.warn("KIS WS subscribe send failed keyId={}: {}", keyId, ex.toString());
                        webSocketRef = null;
                        markFailure("send");
                        scheduleReconnect();
                    } else {
                        log.debug("KIS WS subscribe sent keyId={} code={}", keyId, stockCode);
                    }
                });
            } catch (Exception e) {
                log.warn("KIS WS subscribe failed keyId={}: {}", keyId, e.toString());
                webSocketRef = null;
                scheduleReconnect();
            }
        }
    }

    /**
     * 구독 해제. KIS tr_type "2" 전송 후 로컬에서 제거. 69→70 변동 시 슬롯 반환용.
     */
    public void removeSubscription(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        if (!subscribedCodes.contains(stockCode)) {
            pendingCodes.remove(stockCode);
            return;
        }
        String approvalKey;
        try {
            approvalKey = approvalKeyProvider.getApprovalKey(keyId);
        } catch (Exception e) {
            log.debug("KIS WS unsubscribe skipped (approval key) keyId={}: {}", keyId, e.getMessage());
            subscribedCodes.remove(stockCode);
            pendingCodes.remove(stockCode);
            return;
        }
        String escaped = approvalKey.replace("\\", "\\\\").replace("\"", "\\\"");
        String unsubscribeJson = "{\"header\":{\"approval_key\":\"" + escaped
                + "\",\"custtype\":\"P\",\"tr_type\":\"2\",\"content-type\":\"utf-8\"}"
                + ",\"body\":{\"input\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"" + stockCode + "\",\"tr_type\":\"2\"}}}";

        synchronized (sendLock) {
            WebSocket ws = webSocketRef;
            if (ws != null) {
                try {
                    ws.sendText(unsubscribeJson, true).whenComplete((w, ex) -> {
                        if (ex != null) {
                            log.warn("KIS WS unsubscribe send failed keyId={}: {}", keyId, ex.toString());
                        } else {
                            log.debug("KIS WS unsubscribe sent keyId={} code={}", keyId, stockCode);
                        }
                    });
                } catch (Exception e) {
                    log.warn("KIS WS unsubscribe failed keyId={}: {}", keyId, e.toString());
                }
            }
            subscribedCodes.remove(stockCode);
            pendingCodes.remove(stockCode);
        }
    }

    /** 연결 시 pending을 비우며 전부 subscribe 전송. */
    public void drainPending() {
        if (!isConnected()) {
            return;
        }
        var toSend = new ArrayList<>(pendingCodes);
        pendingCodes.clear();
        for (String code : toSend) {
            if (subscribedCodes.contains(code)) {
                continue;
            }
            sendSubscribe(code);
            subscribedCodes.add(code);
            log.debug("KIS WS subscribe drained keyId={} code={}", keyId, code);
        }
    }

    /** 연결 종료 시: subscribed → pending, subscribed 비움. */
    public void onDisconnected() {
        pendingCodes.addAll(subscribedCodes);
        subscribedCodes.clear();
    }

    /** 재분배 직전 호출. 복구 후 drainPending으로 동일 종목 재구독하지 않도록 구독 상태 비움. */
    public void clearAllSubscriptions() {
        subscribedCodes.clear();
        pendingCodes.clear();
    }

    private void scheduleReconnect() {
        if (!approvalKeyProvider.isKeyConfigured(keyId)) {
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
                log.info("KIS WS reconnect attempt keyId={}", keyId);
                connect();
            } finally {
                reconnectScheduled = false;
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

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

    /** 배열 인덱스가 범위 내면 해당 값, 아니면 빈 문자열(파싱 시 null 반환). H0STCNT0 필드 개수 차이 대응. */
    private static String getField(String[] fields, int index) {
        if (fields == null || index < 0 || index >= fields.length) return "";
        String v = fields[index];
        return v != null ? v : "";
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
