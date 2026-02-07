package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.ChatMessage;
import com.uniport.repository.ChatMessageRepository;
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

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ChatMessage saveMessage(Long roomId, Long userId, String userNickname, String message) {
        ChatMessage msg = ChatMessage.of(roomId, userId, userNickname, message != null ? message : "");
        return chatMessageRepository.save(msg);
    }

    /** 투자계획 공유용: type=trade, tradeData 저장 시 message에 JSON 문자열로 저장 */
    @Transactional
    public ChatMessage saveTradeMessage(Long roomId, Long userId, String userNickname, Map<String, Object> tradeData) {
        try {
            Map<String, Object> payload = Map.of("type", "trade", "tradeData", tradeData != null ? tradeData : Map.of());
            String message = OBJECT_MAPPER.writeValueAsString(payload);
            ChatMessage msg = ChatMessage.of(roomId, userId, userNickname, message);
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
            } catch (Exception ignored) {
            }
        }
        map.put("type", "user");
        map.put("message", msg);
        map.put("tradeData", null);
        return map;
    }
}
