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
        return chatMessageRepository.save(msg);
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
            return chatMessageRepository.save(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save trade message", e);
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
            } catch (Exception ignored) {
            }
        }
        map.put("type", "user");
        map.put("message", msg);
        map.put("tradeData", null);
        return map;
    }
}
