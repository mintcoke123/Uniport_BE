package com.uniport.websocket;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 그룹 채팅방별 WebSocket 세션 보관 및 브로드캐스트.
 * 체결 알림 등 서버 발신 메시지를 해당 그룹에 연결된 모든 클라이언트에 실시간 전달할 때 사용.
 */
public class GroupChatBroadcaster {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> groupSessions = new ConcurrentHashMap<>();

    public void addSession(String groupId, WebSocketSession session) {
        if (groupId == null || session == null) return;
        groupSessions.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        groupSessions.values().forEach(set -> set.remove(session));
    }

    /** 해당 그룹에 연결된 모든 세션에 메시지 전송 (실시간 체결 알림 등) */
    public void broadcast(String groupId, String jsonMessage) {
        Set<WebSocketSession> set = groupSessions.get(groupId);
        if (set == null) return;
        TextMessage msg = new TextMessage(jsonMessage);
        set.stream()
                .filter(WebSocketSession::isOpen)
                .forEach(s -> sendSafe(s, msg));
    }

    private static void sendSafe(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException ignored) {
        }
    }
}
