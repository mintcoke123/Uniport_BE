package com.uniport.service;

import com.uniport.dto.NewsSharePreviewDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.websocket.GroupChatBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceNewsShareTest {

    @Test
    void mapsNewsSharePayloadToChatMessageResponse() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(77L);
            return message;
        });

        ChatMessage saved = service.saveNewsShareMessage(
                1L,
                2L,
                "뉴스공유러",
                NewsSharePreviewDTO.builder()
                        .id("news_001")
                        .categoryLabel("시황")
                        .title("코스피, 반도체 강세에 장 초반 상승 출발")
                        .summary("외국인 순매수가 지수 흐름을 이끌고 있어요.")
                        .build()
        );
        when(repository.findByRoomIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(saved));

        Map<String, Object> mapped = service.getMessages(1L).get(0);

        assertEquals("NEWS_SHARE", mapped.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> news = (Map<String, Object>) mapped.get("news");
        assertEquals("news_001", news.get("id"));
        assertEquals("시황", news.get("categoryLabel"));
        assertNull(mapped.get("message"));
    }
}
