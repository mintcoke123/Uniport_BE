package com.uniport.service.kisws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * KIS 실시간 WebSocket 연결 핸들러.
 * 연결 성공/종료/오류만 로그. 구독·메시지 파싱은 하지 않음.
 */
public class KisWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketHandler.class);

    private final Runnable onCloseCallback;

    public KisWebSocketHandler(Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("KIS WebSocket connected");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // 구독/파싱 미구현
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("KIS WebSocket connection closed");
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("KIS WebSocket transport error");
    }
}
