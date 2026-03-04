package com.uniport.websocket;

import com.uniport.service.kisws.KisWsSubscriptionManager;
import com.uniport.service.kisws.PriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 실시간 시세 푸시: KIS WS 수신 시 구독 중인 클라이언트에게 브로드캐스트.
 * /prices 클라이언트가 subscribe 메시지로 보내는 종목 목록에 따라 (1) 해당 세션에 대한 전송 대상 갱신,
 * (2) 어떤 종목도 /prices 클라이언트가 구독하지 않으면 KIS API 구독 해제로 반영.
 */
@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    /** 종목코드 -> 해당 종목을 구독한 세션들 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> codeToSessions = new ConcurrentHashMap<>();
    /** 세션 -> 구독 중인 종목코드들 (연결 종료 시 정리용) */
    private final ConcurrentHashMap<WebSocketSession, Set<String>> sessionToCodes = new ConcurrentHashMap<>();
    /** 세션별 send 직렬화용 락 (키: session.getId()). 동시 sendMessage로 인한 TEXT_PARTIAL_WRITING 방지. */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private final KisWsSubscriptionManager kisWsSubscriptionManager;

    /** 구독 메트릭: 병목/용량 판정용. (41*키개수) 대비 peak_unique_codes, 분당 add/remove peak */
    private final Object metricsLock = new Object();
    private long metricsMinuteBucket = -1L;
    private int addThisMinute = 0;
    private int removeThisMinute = 0;
    private final AtomicInteger peakUniqueCodes = new AtomicInteger(0);
    private final AtomicInteger peakAddPerMin = new AtomicInteger(0);
    private final AtomicInteger peakRemovePerMin = new AtomicInteger(0);
    private int metricsLastDay = -1;

    public PriceBroadcaster(@Lazy KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
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
        Set<String> globalBefore = globalSubscribedCodes();
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
        Set<String> globalAfter = globalSubscribedCodes();
        syncKisSubscriptions(globalBefore, globalAfter);
        log.info("Price WS subscribe sessionId={} codes={}", session.getId(), normalized);
    }

    /** 세션 연결 해제 시 호출. 그 세션만 구독하던 종목은 전역에서 빠지면 KIS 구독 해제. */
    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Set<String> globalBefore = globalSubscribedCodes();
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
        Set<String> globalAfter = globalSubscribedCodes();
        syncKisSubscriptions(globalBefore, globalAfter);
        sessionLocks.remove(session.getId());
    }

    /** 전역 구독 집합 변동만 KIS에 반영: 빠진 종목은 unsubscribe, 새로 생긴 종목은 subscribe */
    private void syncKisSubscriptions(Set<String> before, Set<String> after) {
        Set<String> toRemove = before.stream().filter(c -> !after.contains(c)).collect(Collectors.toSet());
        Set<String> toAdd = after.stream().filter(c -> !before.contains(c)).collect(Collectors.toSet());

        recordSubscriptionMetrics(after.size(), toAdd.size(), toRemove.size());

        for (String code : toRemove) {
            try {
                kisWsSubscriptionManager.removeSubscription(code);
                log.debug("Price WS KIS unsubscribe (no /prices clients): {}", code);
            } catch (Exception e) {
                log.debug("Price WS KIS unsubscribe failed {}: {}", code, e.getMessage());
            }
        }
        for (String code : toAdd) {
            try {
                kisWsSubscriptionManager.ensureSubscribed(code);
                log.debug("Price WS KIS subscribe (/prices client): {}", code);
            } catch (Exception e) {
                log.debug("Price WS KIS subscribe failed {}: {}", code, e.getMessage());
            }
        }
    }

    /**
     * 구독 변동 메트릭 누적 및 분 단위 peak 로깅.
     * 판정: peak_unique_codes가 (41*키개수) 대비 여유 있으면 확장 여유, peak_add/remove_per_min이 크면 순간 과부하 위험.
     */
    private void recordSubscriptionMetrics(int uniqueCodesNow, int addCount, int removeCount) {
        synchronized (metricsLock) {
            long now = System.currentTimeMillis();
            long minuteBucket = now / 60_000L;
            int today = (int) (now / 86400_000L);

            if (today != metricsLastDay) {
                metricsLastDay = today;
                peakUniqueCodes.set(0);
                peakAddPerMin.set(0);
                peakRemovePerMin.set(0);
            }

            if (minuteBucket != metricsMinuteBucket) {
                if (metricsMinuteBucket >= 0) {
                    peakAddPerMin.updateAndGet(curr -> Math.max(curr, addThisMinute));
                    peakRemovePerMin.updateAndGet(curr -> Math.max(curr, removeThisMinute));
                    log.info("Price WS metrics peak_unique_codes={} peak_add_per_min={} peak_remove_per_min={} (current_unique={})",
                            peakUniqueCodes.get(), peakAddPerMin.get(), peakRemovePerMin.get(), uniqueCodesNow);
                }
                metricsMinuteBucket = minuteBucket;
                addThisMinute = 0;
                removeThisMinute = 0;
            }

            addThisMinute += addCount;
            removeThisMinute += removeCount;
            peakUniqueCodes.updateAndGet(curr -> Math.max(curr, uniqueCodesNow));
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
