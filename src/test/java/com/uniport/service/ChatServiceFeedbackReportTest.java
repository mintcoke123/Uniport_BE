package com.uniport.service;

import com.uniport.entity.ChatMessage;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.websocket.GroupChatBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceFeedbackReportTest {

    @Test
    void mapsFeedbackReportPayloadToChatMessageResponse() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.findByRoomIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(99L);
            return message;
        });

        ChatMessage saved = service.saveFeedbackReportMessage(
                1L,
                7L,
                Map.of(
                        "reportId", 7L,
                        "returnRate", 18.5,
                        "aiComment", "삼성전자 거래가 성과에 기여했어요."
                )
        );
        when(repository.findByRoomIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(saved));

        Map<String, Object> mapped = service.getMessages(1L).get(0);

        assertEquals("GROUP_INVESTMENT_FEEDBACK_REPORT", mapped.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) mapped.get("report");
        assertEquals(7, ((Number) report.get("reportId")).longValue());
        assertEquals(null, mapped.get("message"));
    }
}
