package com.uniport.service.kisws.multi;

import com.uniport.service.kisws.PriceCache;
import com.uniport.service.kisws.StockRealtimeCache;
import com.uniport.websocket.PriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KeyContext 집합 관리. keys 기반으로 KeyContext 생성 후 각각 connect.
 * ensureSubscribed는 primary/fallback 라우팅. DOWN 키 재분배 지원.
 * Step3: REST용 KisRestClient 보관, pickRestKeyIdWithFallback 제공.
 */
@Component
public class KeyPool {

    private static final Logger log = LoggerFactory.getLogger(KeyPool.class);

    private static final int WS_LIMITER_CAPACITY = 50;
    private static final double WS_LIMITER_REFILL = 20.0;
    /** KIS 공지: 실전 REST 20건/초·계좌, 모의 2건/초 */
    private static final int REST_LIMITER_CAPACITY_REAL = 20;
    private static final double REST_LIMITER_REFILL_REAL = 10.0;
    private static final int REST_LIMITER_CAPACITY_MOCK = 2;
    private static final double REST_LIMITER_REFILL_MOCK = 2.0;

    private final KisKeyProperties kisKeyProperties;
    private final ApprovalKeyProvider approvalKeyProvider;
    private final StockRealtimeCache stockRealtimeCache;
    private final PriceCache priceCache;
    private final PriceBroadcaster priceBroadcaster;
    private final RestTemplate restTemplate;
    private final boolean useMock;
    private final String baseUrl;
    private final String baseUrlMock;
    private final String defaultAppkey;
    private final String defaultAppsecret;

    private final List<KeyContext> contexts = new ArrayList<>();
    private final Map<String, TokenBucketLimiter> restLimiters = new ConcurrentHashMap<>();
    private final Map<String, KisRestClient> restClients = new ConcurrentHashMap<>();

    public KeyPool(KisKeyProperties kisKeyProperties,
                   ApprovalKeyProvider approvalKeyProvider,
                   StockRealtimeCache stockRealtimeCache,
                   PriceCache priceCache,
                   PriceBroadcaster priceBroadcaster,
                   RestTemplate restTemplate,
                   @Value("${kis.api.use-mock:false}") boolean useMock,
                   @Value("${kis.api.base-url:https://openapi.koreainvestment.com:9443}") String baseUrl,
                   @Value("${kis.api.base-url-mock:https://openapivts.koreainvestment.com:29443}") String baseUrlMock,
                   @Value("${kis.api.appkey:}") String defaultAppkey,
                   @Value("${kis.api.appsecret:}") String defaultAppsecret) {
        this.kisKeyProperties = kisKeyProperties;
        this.approvalKeyProvider = approvalKeyProvider;
        this.stockRealtimeCache = stockRealtimeCache;
        this.priceCache = priceCache;
        this.priceBroadcaster = priceBroadcaster;
        this.restTemplate = restTemplate;
        this.useMock = useMock;
        this.baseUrl = baseUrl != null ? baseUrl : "https://openapi.koreainvestment.com:9443";
        this.baseUrlMock = baseUrlMock != null ? baseUrlMock : "https://openapivts.koreainvestment.com:29443";
        this.defaultAppkey = defaultAppkey != null ? defaultAppkey.trim() : "";
        this.defaultAppsecret = defaultAppsecret != null ? defaultAppsecret.trim() : "";
    }

    @PostConstruct
    public void init() {
        List<KeyCredential> keys = kisKeyProperties.getKeys();
        List<String> keyIds = new ArrayList<>();
        if (keys != null && !keys.isEmpty()) {
            for (KeyCredential k : keys) {
                if (k.getId() != null && !k.getId().isBlank()
                        && approvalKeyProvider.isKeyConfigured(k.getId())) {
                    keyIds.add(k.getId());
                }
            }
        }
        if (keyIds.isEmpty() && approvalKeyProvider.isKeyConfigured("default")) {
            keyIds.add("default");
        }
        for (String keyId : keyIds) {
            KeyCircuitBreaker circuitBreaker = new KeyCircuitBreaker(keyId);
            TokenBucketLimiter wsLimiter = new TokenBucketLimiter(WS_LIMITER_CAPACITY, WS_LIMITER_REFILL);
            TokenBucketLimiter restLimiter = useMock
                    ? new TokenBucketLimiter(REST_LIMITER_CAPACITY_MOCK, REST_LIMITER_REFILL_MOCK)
                    : new TokenBucketLimiter(REST_LIMITER_CAPACITY_REAL, REST_LIMITER_REFILL_REAL);
            restLimiters.put(keyId, restLimiter);
            String appkey;
            String appsecret;
            if ("default".equals(keyId)) {
                appkey = defaultAppkey;
                appsecret = defaultAppsecret;
            } else {
                KeyCredential cred = findCredential(keyId);
                appkey = cred != null && cred.getAppkey() != null ? cred.getAppkey().trim() : "";
                appsecret = cred != null && cred.getAppsecret() != null ? cred.getAppsecret().trim() : "";
            }
            if (appkey.isBlank() || appsecret.isBlank()) {
                continue;
            }
            KisRestClient restClient = new KisRestClient(keyId, restTemplate, baseUrl, baseUrlMock, useMock,
                    appkey, appsecret, circuitBreaker, restLimiter);
            restClients.put(keyId, restClient);
            KeyContext ctx = new KeyContext(keyId, approvalKeyProvider, stockRealtimeCache,
                    priceCache, priceBroadcaster, useMock, circuitBreaker, wsLimiter);
            contexts.add(ctx);
            ctx.connect();
        }
        if (contexts.isEmpty()) {
            log.warn("KIS KeyPool initialized with 0 key contexts. WebSocket connections will not be started.");
        } else {
            log.info("KIS KeyPool initialized with {} key(s)", contexts.size());
        }
    }

    private KeyCredential findCredential(String keyId) {
        List<KeyCredential> keys = kisKeyProperties.getKeys();
        if (keys == null) return null;
        for (KeyCredential k : keys) {
            if (keyId.equals(k.getId())) return k;
        }
        return null;
    }

    /** REST 클라이언트 존재 여부. 사용 가능한(isConfigured) 클라이언트가 1개라도 있으면 true. */
    public boolean hasAnyRestClient() {
        if (restClients == null || restClients.isEmpty()) return false;
        for (KisRestClient c : restClients.values()) {
            if (c != null && c.isConfigured()) return true;
        }
        return false;
    }

    public List<KeyContext> getContexts() {
        return List.copyOf(contexts);
    }

    public KisRestClient getRestClient(String keyId) {
        return keyId != null ? restClients.get(keyId) : null;
    }

    /** primary가 없거나 비가용이면 fallback keyId. restClients 존재 + isAvailable 필터. */
    public String pickRestKeyIdWithFallback(String stockCode) {
        String primaryKeyId = pickRestKeyId(stockCode);
        KisRestClient primaryClient = getRestClient(primaryKeyId);
        if (primaryClient != null && primaryClient.isAvailable()) {
            return primaryKeyId;
        }
        return pickRestKeyIdWithFallbackExcluding(stockCode, primaryKeyId);
    }

    /** REST fallback 재시도용: excludeKeyId 제외한 가용 keyId. restClients 존재 + isAvailable 필터. */
    public String pickRestKeyIdWithFallbackExcluding(String stockCode, String excludeKeyId) {
        List<String> available = collectAvailableRestKeyIdsExcluding(excludeKeyId);
        if (available.isEmpty()) return null;
        int hash = (stockCode == null || stockCode.isBlank()) ? 0 : stockCode.hashCode();
        int idx = (hash & 0x7FFFFFFF) % available.size();
        return available.get(idx);
    }

    /** excludeKeyId 제외, restClients에 존재하고 isAvailable()인 keyId 목록. default 우선, 이후 contexts 순. */
    private List<String> collectAvailableRestKeyIdsExcluding(String excludeKeyId) {
        List<String> out = new ArrayList<>();
        KisRestClient defaultClient = restClients.get("default");
        if (defaultClient != null && defaultClient.isAvailable() && !"default".equals(excludeKeyId)) {
            out.add("default");
        }
        for (KeyContext ctx : contexts) {
            String id = ctx.getKeyId();
            if (id != null && !out.contains(id) && !id.equals(excludeKeyId)) {
                KisRestClient c = restClients.get(id);
                if (c != null && c.isAvailable()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** getAccessToken/getWebSocketApprovalKey용: default 또는 첫 available. */
    public KisRestClient getDefaultOrFirstRestClient() {
        KisRestClient c = restClients.get("default");
        if (c != null && c.isAvailable()) return c;
        for (KisRestClient client : restClients.values()) {
            if (client != null && client.isAvailable()) return client;
        }
        return c != null ? c : restClients.values().stream().filter(r -> r != null).findFirst().orElse(null);
    }

    /** stockCode 없는 REST 호출용 키 후보: 가용 키만. default 우선, 이후 context 순, 중복 제거. */
    public List<String> getRestKeyIdsToTry() {
        List<String> out = collectAvailableRestKeyIdsExcluding(null);
        return new ArrayList<>(out);
    }

    private KeyContext pickPrimary(String stockCode) {
        return SymbolRouter.pick(contexts, stockCode);
    }

    /** hash 기반 시작 인덱스에서 순회하며 첫 available 선택. available 0개면 null. */
    private KeyContext pickFallback(String stockCode) {
        return pickFallbackExcluding(stockCode, null);
    }

    /** excludeKeyId 제외한 키 중 첫 available. */
    private KeyContext pickFallbackExcluding(String stockCode, String excludeKeyId) {
        if (contexts.isEmpty()) return null;
        int hash = (stockCode == null || stockCode.isBlank()) ? 0 : stockCode.hashCode();
        int start = (hash & 0x7FFFFFFF) % contexts.size();
        int n = contexts.size();
        for (int i = 0; i < n; i++) {
            KeyContext ctx = contexts.get((start + i) % n);
            if (excludeKeyId != null && excludeKeyId.equals(ctx.getKeyId())) continue;
            if (ctx.isAvailable()) return ctx;
        }
        return null;
    }

    /** excludeKeyId 제외한 키 중 available 이면서 구독 여유(41 미만) 있는 키. 70종목 등 분산용. */
    private KeyContext pickFallbackWithCapacityExcluding(String stockCode, String excludeKeyId) {
        if (contexts.isEmpty()) return null;
        int hash = (stockCode == null || stockCode.isBlank()) ? 0 : stockCode.hashCode();
        int start = (hash & 0x7FFFFFFF) % contexts.size();
        int n = contexts.size();
        for (int i = 0; i < n; i++) {
            KeyContext ctx = contexts.get((start + i) % n);
            if (excludeKeyId != null && excludeKeyId.equals(ctx.getKeyId())) continue;
            if (ctx.isAvailable() && ctx.canAcceptMore()) return ctx;
        }
        return null;
    }

    /**
     * 구독 보장. primary 사용, 비가용 또는 41건 초과 시 여유 있는 다른 키로 fallback.
     * KIS 공지: 1세션당 41건 제한. 70종목 등은 2키 이상이면 정상 분산.
     */
    public void ensureSubscribed(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        KeyContext primary = pickPrimary(stockCode);
        KeyContext ctx = null;
        if (primary != null && primary.isAvailable() && primary.canAcceptMore()) {
            ctx = primary;
        } else {
            ctx = pickFallbackWithCapacityExcluding(stockCode, primary != null ? primary.getKeyId() : null);
            if (ctx == null) {
                ctx = pickFallbackExcluding(stockCode, primary != null ? primary.getKeyId() : null);
            }
            if (ctx != null && primary != null) {
                log.warn("KIS KeyPool using fallback key for subscribe stockCode={} primaryKeyId={} fallbackKeyId={}", stockCode, primary.getKeyId(), ctx.getKeyId());
            }
        }
        if (ctx != null && ctx.canAcceptMore()) {
            ctx.ensureSubscribed(stockCode);
        }
    }

    /**
     * 구독 해제. 해당 종목을 보유한 키에서만 KIS에 해제 전송 후 슬롯 반환. 69↔70 변동 시나리오용.
     * 구독 중인 키가 없으면 각 키의 pending에서만 제거.
     */
    public void removeSubscription(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        for (KeyContext ctx : contexts) {
            if (ctx.hasSubscription(stockCode)) {
                ctx.removeSubscription(stockCode);
                return;
            }
        }
        for (KeyContext ctx : contexts) {
            ctx.removeSubscription(stockCode);
        }
    }

    /**
     * fromKeyId에 매핑된 종목을 다른 키로 재분배.
     * pickFallbackExcluding으로 고른 target에만 target.ensureSubscribed(code) 직접 호출(primary 재라우팅 경로 사용 안 함).
     * 재분배 직전 fromCtx.clearAllSubscriptions()로 복구 후 drainPending 중복 구독 방지.
     */
    public void redistributeFrom(String fromKeyId, String reason) {
        KeyContext fromCtx = null;
        for (KeyContext c : contexts) {
            if (fromKeyId.equals(c.getKeyId())) {
                fromCtx = c;
                break;
            }
        }
        if (fromCtx == null) return;
        var codes = fromCtx.snapshotSubscribedCodes();
        fromCtx.clearAllSubscriptions();
        for (String code : codes) {
            KeyContext target = pickFallbackExcluding(code, fromKeyId);
            if (target != null) {
                target.ensureSubscribed(code);
            }
        }
    }

    public boolean anyConnected() {
        for (KeyContext ctx : contexts) {
            if (ctx.isConnected()) {
                return true;
            }
        }
        return false;
    }

    public void forceReconnectAll(String reason) {
        for (KeyContext ctx : contexts) {
            ctx.forceReconnect(reason);
        }
    }

    /** Step3에서 KisApiService REST 호출 전 사용. Step2에서는 골격만. */
    public boolean tryAcquireRestPermit(String keyId) {
        TokenBucketLimiter limiter = restLimiters.get(keyId);
        return limiter != null && limiter.tryAcquire();
    }

    /** WS와 동일 primary/fallback 라우팅으로 keyId 반환. Step3에서 REST 키 선택용. */
    public String pickRestKeyId(String stockCode) {
        KeyContext primary = pickPrimary(stockCode);
        KeyContext ctx = (primary != null && primary.isAvailable()) ? primary : pickFallback(stockCode);
        return ctx != null ? ctx.getKeyId() : null;
    }

    @PreDestroy
    public void shutdown() {
        for (KeyContext ctx : contexts) {
            ctx.shutdown();
        }
    }
}

