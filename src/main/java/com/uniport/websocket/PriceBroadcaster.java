package com.uniport.websocket;

import com.uniport.service.kisws.PriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실시간 시세 푸시: KIS WS 수신 시 구독 중인 클라이언트에게 브로드캐스트.
 */
@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    /** 종목코드 -> 해당 종목을 구독한 세션들 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> codeToSessions = new ConcurrentHashMap<>();
    /** 세션 -> 구독 중인 종목코드들 (연결 종료 시 정리용) */
    private final ConcurrentHashMap<WebSocketSession, Set<String>> sessionToCodes = new ConcurrentHashMap<>();

    /** 클라이언트가 구독할 종목코드 설정. 기존 구독은 교체됨. */
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
    }

    /** 세션 연결 해제 시 호출 */
    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Set<String> codes = sessionToCodes.remove(session);
        if (codes != null) {
            for (String code : codes) {
                Set<WebSocketSession> set = codeToSessions.get(code);
                if (set != null) {
                    set.remove(session);
                }
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
