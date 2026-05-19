package com.uniport.service;

import com.uniport.dto.InvestmentIssueSharePreviewDTO;
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

    @Test
    void mapsInvestmentIssueSharePayloadToChatMessageResponse() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(88L);
            return message;
        });

        ChatMessage saved = service.saveInvestmentIssueShareMessage(
                1L,
                2L,
                "이슈공유러",
                InvestmentIssueSharePreviewDTO.builder()
                        .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                        .title("HBM 기대감에 반도체주 강세")
                        .label("positive")
                        .labelText("호재")
                        .summary("AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리고 있어요.")
                        .relatedStocks(List.of("삼성전자", "SK하이닉스"))
                        .sourceCount(6)
                        .build()
        );
        when(repository.findByRoomIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(saved));

        Map<String, Object> mapped = service.getMessages(1L).get(0);

        assertEquals(ChatService.TYPE_INVESTMENT_ISSUE_SHARE, mapped.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> issue = (Map<String, Object>) mapped.get("issue");
        assertEquals("issue_20260519_hbm_semiconductor_8f3a12", issue.get("issueId"));
        assertEquals("HBM 기대감에 반도체주 강세", issue.get("title"));
        assertEquals("호재", issue.get("labelText"));
        assertNull(mapped.get("message"));
    }

    @Test
    void parsesLegacyNewsShareAndInvestmentIssueSharePayloadsIndependently() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        ChatService service = new ChatService(repository, broadcaster);

        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage newsMessage = service.saveNewsShareMessage(
                1L,
                2L,
                "뉴스공유러",
                NewsSharePreviewDTO.builder()
                        .id("news_001")
                        .categoryLabel("시황")
                        .title("코스피 상승")
                        .summary("반도체주가 지수 흐름을 이끌고 있어요.")
                        .build()
        );
        ChatMessage issueMessage = service.saveInvestmentIssueShareMessage(
                1L,
                3L,
                "이슈공유러",
                InvestmentIssueSharePreviewDTO.builder()
                        .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                        .title("HBM 기대감에 반도체주 강세")
                        .label("positive")
                        .labelText("호재")
                        .summary("AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리고 있어요.")
                        .relatedStocks(List.of("삼성전자"))
                        .sourceCount(6)
                        .build()
        );
        when(repository.findByRoomIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(newsMessage, issueMessage));

        List<Map<String, Object>> messages = service.getMessages(1L);

        assertEquals(ChatService.TYPE_NEWS_SHARE, messages.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> news = (Map<String, Object>) messages.get(0).get("news");
        assertEquals("news_001", news.get("id"));

        assertEquals(ChatService.TYPE_INVESTMENT_ISSUE_SHARE, messages.get(1).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> issue = (Map<String, Object>) messages.get(1).get("issue");
        assertEquals("issue_20260519_hbm_semiconductor_8f3a12", issue.get("issueId"));
    }
}
