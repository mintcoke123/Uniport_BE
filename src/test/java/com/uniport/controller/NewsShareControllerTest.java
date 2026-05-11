package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.NewsSharePreviewDTO;
import com.uniport.dto.NewsShareRequestDTO;
import com.uniport.dto.NewsShareResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsShareControllerTest {

    @Test
    void shareNewsToChatRoom_resolvesCurrentUserAndDelegatesToService() throws Exception {
        NewsService newsService = mock(NewsService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder().nickname("뉴스공유러").build();
        currentUser.setId(7L);
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(newsService.shareNews(eq(3L), eq(currentUser), any(NewsShareRequestDTO.class)))
                .thenReturn(NewsShareResponseDTO.builder()
                        .messageId(99L)
                        .chatRoomId(3L)
                        .type("NEWS_SHARE")
                        .news(NewsSharePreviewDTO.builder()
                                .id("news_001")
                                .categoryLabel("시황")
                                .title("코스피, 반도체 강세에 장 초반 상승 출발")
                                .summary("외국인 순매수가 지수 흐름을 이끌고 있어요.")
                                .build())
                        .createdAt("2026-05-11T03:00:00Z")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NewsShareController(newsService, currentUserResolver)).build();

        mockMvc.perform(post("/api/chatrooms/3/messages/news")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsId\":\"news_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(99))
                .andExpect(jsonPath("$.type").value("NEWS_SHARE"))
                .andExpect(jsonPath("$.news.id").value("news_001"));

        verify(newsService).shareNews(eq(3L), eq(currentUser), any(NewsShareRequestDTO.class));
    }
}
