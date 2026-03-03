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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private final KisWsSubscriptionManager kisWsSubscriptionManager;

    public PriceBroadcaster(@Lazy KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
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
    }

    /** 세션 연결 해제 시 호출. 그 세션만 구독하던 종목은 전역에서 빠지면 KIS 구독 해제. */
    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Set<String> globalBefore = globalSubscribedCodes();
        Set<String> codes = sessionToCodes.remove(session);
        if (codes != null) {
            for (String code : codes) {
                Set<WebSocketSession> set = codeToSessions.get(code);
                if (set != null) {
                    set.remove(session);
                }
            }
        }
        Set<String> globalAfter = globalSubscribedCodes();
        syncKisSubscriptions(globalBefore, globalAfter);
    }

    /** 전역 구독 집합 변동만 KIS에 반영: 빠진 종목은 unsubscribe, 새로 생긴 종목은 subscribe */
    private void syncKisSubscriptions(Set<String> before, Set<String> after) {
        Set<String> toRemove = before.stream().filter(c -> !after.contains(c)).collect(Collectors.toSet());
        Set<String> toAdd = after.stream().filter(c -> !before.contains(c)).collect(Collectors.toSet());
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
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.debug("Price broadcast send failed: {}", e.getMessage());
        }
    }
}
