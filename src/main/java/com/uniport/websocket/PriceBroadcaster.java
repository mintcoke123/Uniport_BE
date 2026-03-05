package com.uniport.websocket;

import com.uniport.service.kisws.KisWsSubscriptionManager;
import com.uniport.service.kisws.PriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * 실시간 시세 푸시: KIS WS 수신 시 구독 중인 클라이언트에게 브로드캐스트.
 * /prices 클라이언트가 subscribe 메시지로 보내는 종목 목록에 따라 (1) 해당 세션에 대한 전송 대상 갱신,
 * (2) 어떤 종목도 /prices 클라이언트가 구독하지 않으면 KIS API 구독 해제로 반영.
 */
@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    private static final long UNSUBSCRIBE_TTL_MS = 30_000L;
    private static final long SYNC_BATCH_DELAY_MS = 300L;

    /** 종목코드 -> 해당 종목을 구독한 세션들 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> codeToSessions = new ConcurrentHashMap<>();
    /** 세션 -> 구독 중인 종목코드들 (연결 종료 시 정리용) */
    private final ConcurrentHashMap<WebSocketSession, Set<String>> sessionToCodes = new ConcurrentHashMap<>();
    /** 세션별 send 직렬화용 락 (키: session.getId()). 동시 sendMessage로 인한 TEXT_PARTIAL_WRITING 방지. */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private final KisWsSubscriptionManager kisWsSubscriptionManager;

    /** Unsubscribe TTL: key=stockCode, value=unsubscribe 예약 시각(ms). 30초 후 실제 KIS unsubscribe. */
    private final ConcurrentHashMap<String, Long> pendingUnsubscribe = new ConcurrentHashMap<>();
    /** 300ms 배치용 스케줄러 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "price-sync-batch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean syncScheduled = new AtomicBoolean(false);
    /** 마지막 sync 시점의 전역 구독 스냅샷. scheduleSync()에서 before로 사용 */
    private final AtomicReference<Set<String>> lastGlobalSnapshot = new AtomicReference<>(Set.of());

    /** 구독 메트릭: pending_remove(=toRemove put), ttl_unsubscribe(=실제 KIS unsubscribe 호출) 분리 */
    private final Object metricsLock = new Object();
    private long metricsMinuteBucket = -1L;
    private int addThisMinute = 0;
    private int pendingRemoveThisMinute = 0;
    private int ttlUnsubscribeThisMinute = 0;
    private final AtomicInteger peakUniqueCodes = new AtomicInteger(0);
    private final AtomicInteger peakAddPerMin = new AtomicInteger(0);
    private final AtomicInteger peakPendingRemovePerMin = new AtomicInteger(0);
    private final AtomicInteger peakTtlUnsubscribePerMin = new AtomicInteger(0);
    private final AtomicInteger lastKnownUniqueCodes = new AtomicInteger(0);
    private int metricsLastDay = -1;

    public PriceBroadcaster(@Lazy KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 특정 세션이 구독 중인 종목코드 (없으면 빈 집합, 스냅샷 복사) */
    public Set<String> getSubscribedCodes(WebSocketSession session) {
        if (session == null) return Set.of();
        Set<String> codes = sessionToCodes.get(session);
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    /** 세션 ID(키)별 구독 종목 목록 스냅샷. 키: session.getId(), 값: 해당 세션이 구독 중인 종목코드 집합 */
    public Map<String, Set<String>> getSubscriptionSummaryBySessionId() {
        Map<String, Set<String>> out = new ConcurrentHashMap<>();
        sessionToCodes.forEach((session, codes) -> {
            if (session != null && codes != null) {
                out.put(session.getId(), Set.copyOf(codes));
            }
        });
        return Collections.unmodifiableMap(out);
    }

    /** /prices 세션 전체가 구독 중인 종목코드 합집합 (KIS 동기화용) */
    private Set<String> globalSubscribedCodes() {
        Set<String> out = ConcurrentHashMap.newKeySet();
        sessionToCodes.values().forEach(out::addAll);
        return out;
    }

    /** 클라이언트가 구독할 종목코드 설정. 기존 구독은 교체됨. 전역 집합이 줄면 KIS 구독 해제, 늘면 구독 요청. */
    public void subscribe(WebSocketSession session, Set<String> stockCodes) {
        if (session == null) return;
        Set<String> normalized = ConcurrentHashMap.newKeySet();
        for (String c : stockCodes) {
            if (c != null && !c.isBlank()) {
                String code = c.trim().length() >= 6 ? c.trim() : String.format("%6s", c.trim()).replace(' ', '0');
                normalized.add(code);
            }
        }
        Set<String> prev = sessionToCodes.put(session, normalized);
        if (prev != null) {
            for (String code : prev) {
                if (!normalized.contains(code)) {
                    Set<WebSocketSession> set = codeToSessions.get(code);
                    if (set != null) set.remove(session);
                }
            }
        }
        for (String code : normalized) {
            codeToSessions.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
        scheduleSync();
        log.info("Price WS subscribe sessionId={} codes={}", session.getId(), normalized);
    }

    /** 세션 연결 해제 시 호출. 그 세션만 구독하던 종목은 전역에서 빠지면 KIS 구독 해제. */
    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Set<String> codes = sessionToCodes.remove(session);
        if (codes != null) {
            log.info("Price WS removeSession sessionId={} wasSubscribedTo={}", session.getId(), codes);
            for (String code : codes) {
                Set<WebSocketSession> set = codeToSessions.get(code);
                if (set != null) {
                    set.remove(session);
                }
            }
        }
        scheduleSync();
        sessionLocks.remove(session.getId());
    }

    /**
     * 300ms 후 한 번만 sync 실행. 그 사이 호출된 subscribe/removeSession 변경을 모아서 한 번에 반영.
     */
    private void scheduleSync() {
        if (!syncScheduled.getAndSet(true)) {
            scheduler.schedule(() -> {
                try {
                    Set<String> before = lastGlobalSnapshot.get();
                    Set<String> after = globalSubscribedCodes();
                    syncKisSubscriptions(before, after);
                    lastGlobalSnapshot.set(after);
                } finally {
                    syncScheduled.set(false);
                }
            }, SYNC_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** 전역 구독 집합 변동만 KIS에 반영: toRemove는 30초 TTL로 예약, toAdd는 즉시 subscribe 및 TTL 취소 */
    private void syncKisSubscriptions(Set<String> before, Set<String> after) {
        Set<String> toRemove = before.stream().filter(c -> !after.contains(c)).collect(Collectors.toSet());
        Set<String> toAdd = after.stream().filter(c -> !before.contains(c)).collect(Collectors.toSet());

        log.info("Price WS sync unique={} add={} remove={}", after.size(), toAdd.size(), toRemove.size());
        recordSubscriptionMetrics(after.size(), toAdd.size(), toRemove.size()); // toRemove.size() = pending_remove

        long now = System.currentTimeMillis();
        for (String code : toRemove) {
            pendingUnsubscribe.put(code, now);
        }
        for (String code : toAdd) {
            pendingUnsubscribe.remove(code);
            try {
                kisWsSubscriptionManager.ensureSubscribed(code);
                log.debug("Price WS KIS subscribe (/prices client): {}", code);
            } catch (Exception e) {
                log.debug("Price WS KIS subscribe failed {}: {}", code, e.getMessage());
            }
        }
    }

    /** 5초마다 실행. 30초 TTL 경과한 pendingUnsubscribe만 실제 KIS unsubscribe */
    @Scheduled(fixedDelay = 5000)
    public void processPendingUnsubscribes() {
        long now = System.currentTimeMillis();
        List<String> due = new ArrayList<>();
        pendingUnsubscribe.forEach((code, scheduledTime) -> {
            if (now - scheduledTime >= UNSUBSCRIBE_TTL_MS) {
                due.add(code);
            }
        });
        int ttlUnsubscribeCount = 0;
        for (String code : due) {
            Long scheduledTime = pendingUnsubscribe.get(code);
            if (scheduledTime == null) continue;
            long ageMs = now - scheduledTime;
            pendingUnsubscribe.remove(code);
            if (!globalSubscribedCodes().contains(code)) {
                boolean currentGlobalContains = globalSubscribedCodes().contains(code);
                try {
                    kisWsSubscriptionManager.removeSubscription(code);
                    ttlUnsubscribeCount++;
                    log.info("Price WS TTL unsubscribe code={} ageMs={} currentGlobalContains={}", code, ageMs, currentGlobalContains);
                } catch (Exception e) {
                    log.debug("Price WS KIS unsubscribe failed {}: {}", code, e.getMessage());
                }
            }
        }
        if (ttlUnsubscribeCount > 0) {
            recordTtlUnsubscribeMetrics(ttlUnsubscribeCount);
        }
    }

    /**
     * 구독 변동 메트릭 누적 및 분 단위 peak 로깅.
     * pending_remove = toRemove 발생(pendingUnsubscribe put), ttl_unsubscribe = 실제 KIS unsubscribe 호출 횟수.
     */
    private void recordSubscriptionMetrics(int uniqueCodesNow, int addCount, int pendingRemoveCount) {
        synchronized (metricsLock) {
            lastKnownUniqueCodes.set(uniqueCodesNow);
            long now = System.currentTimeMillis();
            long minuteBucket = now / 60_000L;
            int today = (int) (now / 86400_000L);

            if (today != metricsLastDay) {
                metricsLastDay = today;
                peakUniqueCodes.set(0);
                peakAddPerMin.set(0);
                peakPendingRemovePerMin.set(0);
                peakTtlUnsubscribePerMin.set(0);
            }

            maybeRollMetricsBucket(now, minuteBucket);

            addThisMinute += addCount;
            pendingRemoveThisMinute += pendingRemoveCount;
            peakUniqueCodes.updateAndGet(curr -> Math.max(curr, uniqueCodesNow));
        }
    }

    /** TTL 구간에서 실제 KIS unsubscribe 호출한 횟수 집계. 1분 peak 로그는 maybeRollMetricsBucket에서 통합 출력. */
    private void recordTtlUnsubscribeMetrics(int count) {
        synchronized (metricsLock) {
            long now = System.currentTimeMillis();
            long minuteBucket = now / 60_000L;
            int today = (int) (now / 86400_000L);
            if (today != metricsLastDay) {
                metricsLastDay = today;
                peakUniqueCodes.set(0);
                peakAddPerMin.set(0);
                peakPendingRemovePerMin.set(0);
                peakTtlUnsubscribePerMin.set(0);
            }
            maybeRollMetricsBucket(now, minuteBucket);
            ttlUnsubscribeThisMinute += count;
        }
    }

    private void maybeRollMetricsBucket(long now, long minuteBucket) {
        if (minuteBucket != metricsMinuteBucket) {
            if (metricsMinuteBucket >= 0) {
                peakAddPerMin.updateAndGet(curr -> Math.max(curr, addThisMinute));
                peakPendingRemovePerMin.updateAndGet(curr -> Math.max(curr, pendingRemoveThisMinute));
                peakTtlUnsubscribePerMin.updateAndGet(curr -> Math.max(curr, ttlUnsubscribeThisMinute));
                log.info("Price WS metrics peak_unique_codes={} peak_add_per_min={} peak_pending_remove_per_min={} peak_ttl_unsubscribe_per_min={} (current_unique={})",
                        peakUniqueCodes.get(), peakAddPerMin.get(), peakPendingRemovePerMin.get(), peakTtlUnsubscribePerMin.get(), lastKnownUniqueCodes.get());
            }
            metricsMinuteBucket = minuteBucket;
            addThisMinute = 0;
            pendingRemoveThisMinute = 0;
            ttlUnsubscribeThisMinute = 0;
        }
    }

    /** 해당 종목 시세 갱신 시 구독 클라이언트에게 전송 */
    public void broadcast(String stockCode, PriceSnapshot snapshot) {
        if (stockCode == null || snapshot == null) return;
        Set<WebSocketSession> sessions = codeToSessions.get(stockCode);
        if (sessions == null || sessions.isEmpty()) return;
        String payload = toJson(stockCode, snapshot);
        TextMessage msg = new TextMessage(payload);
        sessions.stream()
                .filter(WebSocketSession::isOpen)
                .forEach(s -> sendSafe(s, msg));
    }

    private static String toJson(String stockCode, PriceSnapshot sn) {
        BigDecimal cp = sn.getCurrentPrice();
        BigDecimal ch = sn.getChange();
        BigDecimal cr = sn.getChangeRate();
        Long vol = sn.getVolume();
        long ts = sn.getUpdatedAtMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"stockCode\":\"").append(escape(stockCode)).append("\"");
        sb.append(",\"currentPrice\":").append(cp != null ? cp : "null");
        sb.append(",\"change\":").append(ch != null ? ch : "null");
        sb.append(",\"changeRate\":").append(cr != null ? cr : "null");
        sb.append(",\"volume\":").append(vol != null ? vol : "null");
        sb.append(",\"updatedAtMillis\":").append(ts);
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void sendSafe(WebSocketSession session, TextMessage message) {
        if (session == null || !session.isOpen()) return;
        Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
        synchronized (lock) {
            try {
                if (!session.isOpen()) return;
                session.sendMessage(message);
            } catch (IOException e) {
                log.debug("Price broadcast send failed: {}", e.getMessage());
            } catch (IllegalStateException e) {
                log.debug("Price broadcast send failed (state): {}", e.getMessage());
            }
        }
    }
}
