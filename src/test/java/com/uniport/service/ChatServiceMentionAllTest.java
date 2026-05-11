package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.ChatMessage;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.websocket.GroupChatBroadcaster;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceMentionAllTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void savesMentionAllPayloadAndMapsItForMessageList() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(123L);
            return message;
        });

        ChatMessage saved = service.saveMentionAllMessage(260L, 1L, "유니포트");
        when(repository.findByRoomIdOrderByCreatedAtAsc(260L)).thenReturn(List.of(saved));

        Map<String, Object> mapped = service.getMessages(260L).get(0);

        assertEquals(123L, mapped.get("id"));
        assertEquals(1L, mapped.get("userId"));
        assertEquals("유니포트", mapped.get("userNickname"));
        assertEquals("mention_all", mapped.get("type"));
        assertEquals("모든 팀원을 호출했어요!", mapped.get("message"));
        assertNull(mapped.get("tradeData"));
    }

    @Test
    void broadcastsMentionAllPayloadToGroupSessions() throws Exception {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(123L);
            return message;
        });

        service.saveMentionAllMessage(260L, 1L, "유니포트");

        ArgumentCaptor<String> groupIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(broadcaster).broadcast(groupIdCaptor.capture(), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = OBJECT_MAPPER.readValue(payloadCaptor.getValue(), Map.class);
        assertEquals("260", groupIdCaptor.getValue());
        assertEquals(123, ((Number) payload.get("id")).longValue());
        assertEquals("mention_all", payload.get("type"));
        assertEquals(1, ((Number) payload.get("userId")).longValue());
        assertEquals("유니포트", payload.get("userNickname"));
        assertEquals("모든 팀원을 호출했어요!", payload.get("message"));
        assertNull(payload.get("tradeData"));
    }
}
