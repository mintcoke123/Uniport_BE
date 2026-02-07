package com.uniport.websocket;

import com.uniport.entity.ChatMessage;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 그룹 단위 채팅 WebSocket 핸들러.
 * 연결 경로: /groups/{groupId}/chat (예: ws://host/groups/1/chat)
 * 메시지 수신 시 DB 저장 후 같은 groupId에 브로드캐스트. 나중에 들어온 사용자는 GET으로 조회 가능.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Pattern USER_ID = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)");
    private static final Pattern NICKNAME = Pattern.compile("\"nickname\"\\s*:\\s*\"([^\"]*)\"|\"userNickname\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final ChatService chatService;
    private final AuthService authService;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;

    /** groupId -> 해당 그룹에 연결된 WebSocket 세션들 */
    private final Map<String, java.util.Set<WebSocketSession>> groupSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatService chatService, AuthService authService,
                                MatchingRoomMemberRepository matchingRoomMemberRepository) {
        this.chatService = chatService;
        this.authService = authService;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String groupId = extractGroupId(session);
        if (groupId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String token = extractToken(session);
        User user = token != null ? authService.getUserFromTokenOrNull("Bearer " + token) : null;
        Long roomId = parseRoomId(groupId);
        if (user == null || roomId == null || !matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(roomId, user.getId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        groupSessions.computeIfAbsent(groupId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String groupId = extractGroupId(session);
        if (groupId == null) return;
        String payload = message.getPayload();
        Long roomIdLong = parseRoomId(groupId);
        if (roomIdLong != null) {
            Long userId = extractLong(USER_ID, payload);
            String nickname = extractString(NICKNAME, payload);
            String msg = extractString(MESSAGE, payload);
            if (msg == null) msg = payload;
            if (userId != null) {
                ChatMessage saved = chatService.saveMessage(roomIdLong, userId, nickname != null ? nickname : "", msg);
                Map<String, Object> broadcast = new HashMap<>();
                broadcast.put("id", saved.getId());
                broadcast.put("type", "user");
                broadcast.put("userId", saved.getUserId());
                broadcast.put("userNickname", saved.getUserNickname());
                broadcast.put("message", saved.getMessage());
                broadcast.put("timestamp", saved.getCreatedAt().toString());
                broadcast.put("tradeData", null);
                broadcastToGroup(groupId, toJson(broadcast));
                return;
            }
        }
        broadcastToGroup(groupId, payload);
    }

    private static Long extractLong(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private static String extractString(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(k.replace("\"", "\\\"")).append("\":");
            if (v == null) sb.append("null");
            else if (v instanceof Number) sb.append(v);
            else sb.append("\"").append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        });
        sb.append("}");
        return sb.toString();
    }

    private static Long parseRoomId(String groupId) {
        if (groupId == null || groupId.isBlank()) return null;
        if (groupId.startsWith("room-")) {
            try {
                return Long.parseLong(groupId.substring(5));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Long.parseLong(groupId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String groupId = extractGroupId(session);
        if (groupId != null) {
            java.util.Set<WebSocketSession> set = groupSessions.get(groupId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) groupSessions.remove(groupId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private String extractGroupId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getPath() == null) return null;
        // path: /groups/{groupId}/chat (또는 groups/{groupId}/chat)
        String path = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
        String[] segments = path.split("/");
        if (segments.length >= 3 && "groups".equals(segments[0]) && "chat".equals(segments[2])) {
            String id = segments[1];
            return id != null && !id.isBlank() ? id : null;
        }
        return null;
    }

    /** 쿼리 파라미터에서 token 추출. 예: ?token=xxx */
    private static String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) return null;
        String query = uri.getRawQuery();
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && "token".equals(param.substring(0, eq).trim())) {
                String value = param.substring(eq + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** 같은 그룹에 연결된 모든 세션(발신자 포함)에 메시지 브로드캐스트 */
    private void broadcastToGroup(String groupId, String message) {
        java.util.Set<WebSocketSession> set = groupSessions.get(groupId);
        if (set == null) return;
        TextMessage msg = new TextMessage(message);
        set.stream()
                .filter(WebSocketSession::isOpen)
                .forEach(s -> sendSafe(s, msg));
    }

    private void sendSafe(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException e) {
        }
    }
}
