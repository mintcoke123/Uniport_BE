package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.ChatMessage;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.websocket.GroupChatBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 그룹(방) 채팅 메시지 저장·조회. DB에 저장되어 나중에 들어온 사용자도 확인 가능.
 * type=trade 메시지는 message 필드에 JSON 저장 후 조회 시 type/tradeData로 변환.
 */
@Service
public class ChatService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatMessageRepository chatMessageRepository;
    private final GroupChatBroadcaster groupChatBroadcaster;

    public ChatService(ChatMessageRepository chatMessageRepository, GroupChatBroadcaster groupChatBroadcaster) {
        this.chatMessageRepository = chatMessageRepository;
        this.groupChatBroadcaster = groupChatBroadcaster;
    }

    @Transactional
    public ChatMessage saveMessage(Long roomId, Long userId, String userNickname, String message) {
        ChatMessage msg = ChatMessage.of(roomId, userId, userNickname, message != null ? message : "");
        ChatMessage saved = chatMessageRepository.save(msg);
        broadcastToGroup(roomId, saved);
        return saved;
    }

    /** 매수/매도 체결 완료 알림용: type=execution, executionData 저장. 채팅 목록에 "OOO 매수 체결 완료" 형태로 표시 */
    @Transactional
    public ChatMessage saveExecutionMessage(Long roomId, Long userId, String userNickname, String action, String stockName, int quantity, java.math.BigDecimal executionPrice) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "execution");
            Map<String, Object> data = new HashMap<>();
            data.put("action", action != null ? action : "");
            data.put("stockName", stockName != null ? stockName : "");
            data.put("quantity", quantity);
            data.put("executionPrice", executionPrice != null ? executionPrice.doubleValue() : 0);
            payload.put("executionData", data);
            String message = OBJECT_MAPPER.writeValueAsString(payload);
            ChatMessage msg = ChatMessage.of(roomId, userId != null ? userId : 0L, userNickname != null ? userNickname : "시스템", message);
            msg = chatMessageRepository.save(msg);
            Map<String, Object> broadcast = new HashMap<>();
            broadcast.put("id", msg.getId());
            broadcast.put("userId", msg.getUserId());
            broadcast.put("userNickname", msg.getUserNickname());
            broadcast.put("timestamp", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
            broadcast.put("type", "execution");
            broadcast.put("executionData", data);
            broadcast.put("message", null);
            broadcast.put("tradeData", null);
            try {
                groupChatBroadcaster.broadcast(String.valueOf(roomId), OBJECT_MAPPER.writeValueAsString(broadcast));
            } catch (Exception ignored) { }
            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save execution message", e);
        }
    }

    /** 투자계획 공유용: type=trade, tradeData 저장. tradeData에 voteId가 있으면 동일 room+voteId 기존 메시지 반환(중복 방지) */
    @Transactional
    public ChatMessage saveTradeMessage(Long roomId, Long userId, String userNickname, Map<String, Object> tradeData) {
        Long voteId = null;
        if (tradeData != null && tradeData.containsKey("voteId")) {
            Object v = tradeData.get("voteId");
            if (v instanceof Number) voteId = ((Number) v).longValue();
        }
        if (voteId != null) {
            var existing = chatMessageRepository.findByRoomIdAndVoteId(roomId, voteId);
            if (existing.isPresent()) return existing.get();
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "trade");
            payload.put("tradeData", tradeData != null ? tradeData : Map.of());
            String message = OBJECT_MAPPER.writeValueAsString(payload);
            ChatMessage msg = ChatMessage.of(roomId, userId, userNickname, message);
            msg.setVoteId(voteId);
            ChatMessage saved = chatMessageRepository.save(msg);
            broadcastToGroup(roomId, saved);
            return saved;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save trade message", e);
        }
    }

    /** 어드민 팀별 피드백: 방당 1건만 유지(멱등). 기존 피드백이 있으면 내용만 갱신 후 브로드캐스트, 없으면 새로 저장. */
    @Transactional
    public ChatMessage saveFeedbackMessage(Long roomId, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "feedback");
            payload.put("content", content != null ? content : "");
            payload.put("chatDisabled", true);
            String messageJson = OBJECT_MAPPER.writeValueAsString(payload);

            List<ChatMessage> roomMessages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
            ChatMessage existingFeedback = null;
            for (int i = roomMessages.size() - 1; i >= 0; i--) {
                ChatMessage m = roomMessages.get(i);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = OBJECT_MAPPER.readValue(m.getMessage(), Map.class);
                    if ("feedback".equals(parsed.get("type"))) {
                        existingFeedback = m;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            ChatMessage msg;
            if (existingFeedback != null) {
                existingFeedback.setMessage(messageJson);
                msg = chatMessageRepository.save(existingFeedback);
            } else {
                msg = ChatMessage.of(roomId, 0L, "관리자", messageJson);
                msg = chatMessageRepository.save(msg);
            }

            Map<String, Object> broadcast = new HashMap<>();
            broadcast.put("id", msg.getId());
            broadcast.put("userId", 0);
            broadcast.put("userNickname", "관리자");
            broadcast.put("timestamp", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
            broadcast.put("type", "feedback");
            broadcast.put("feedbackContent", content != null ? content : "");
            broadcast.put("chatDisabled", true);
            broadcast.put("message", null);
            broadcast.put("tradeData", null);
            groupChatBroadcaster.broadcast(String.valueOf(roomId), OBJECT_MAPPER.writeValueAsString(broadcast));
            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save feedback message", e);
        }
    }

    /** 저장된 메시지를 같은 방 WebSocket 연결된 클라이언트에 실시간 전달 (채팅·투표 공유 시) */
    private void broadcastToGroup(Long roomId, ChatMessage saved) {
        try {
            Map<String, Object> payload = toMap(saved);
            groupChatBroadcaster.broadcast(String.valueOf(roomId), OBJECT_MAPPER.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> getMessages(Long roomId) {
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(ChatMessage m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("userId", m.getUserId());
        map.put("userNickname", m.getUserNickname());
        map.put("timestamp", m.getCreatedAt().toString());

        String msg = m.getMessage();
        if (msg != null && msg.trim().startsWith("{")) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(msg, Map.class);
                if ("trade".equals(parsed.get("type")) && parsed.containsKey("tradeData")) {
                    map.put("type", "trade");
                    map.put("tradeData", parsed.get("tradeData"));
                    map.put("message", null);
                    return map;
                }
                if ("execution".equals(parsed.get("type")) && parsed.containsKey("executionData")) {
                    map.put("type", "execution");
                    map.put("executionData", parsed.get("executionData"));
                    map.put("message", null);
                    map.put("tradeData", null);
                    return map;
                }
                if ("feedback".equals(parsed.get("type")) && parsed.containsKey("content")) {
                    map.put("type", "feedback");
                    map.put("feedbackContent", parsed.get("content"));
                    map.put("chatDisabled", Boolean.TRUE.equals(parsed.get("chatDisabled")));
                    map.put("message", null);
                    map.put("tradeData", null);
                    return map;
                }
            } catch (Exception ignored) {
            }
        }
        map.put("type", "user");
        map.put("message", msg);
        map.put("tradeData", null);
        return map;
    }
}
