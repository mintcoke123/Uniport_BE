package com.uniport.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * 실시간 시세 WebSocket. 경로: /prices
 * 클라이언트 → 서버: {"subscribe": ["005930", "000660"]} (구독할 종목코드 목록, 기존 구독 교체)
 * 서버 → 클라이언트: {"stockCode":"005930","currentPrice":70000,"change":500,"changeRate":0.72,"volume":1234567,"updatedAtMillis":...}
 */
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PriceBroadcaster priceBroadcaster;

    public PriceWebSocketHandler(PriceBroadcaster priceBroadcaster) {
        this.priceBroadcaster = priceBroadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("Price WS connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            JsonNode subscribe = root.path("subscribe");
            if (!subscribe.isMissingNode() && subscribe.isArray()) {
                Set<String> codes = ConcurrentHashMap.newKeySet();
                StreamSupport.stream(subscribe.spliterator(), false)
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::asText)
                        .forEach(codes::add);
                priceBroadcaster.subscribe(session, codes);
                log.debug("Price WS subscribe: {} codes={}", session.getId(), codes);
            }
        } catch (Exception e) {
            log.debug("Price WS message parse failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        priceBroadcaster.removeSession(session);
        log.debug("Price WS closed: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }
}
