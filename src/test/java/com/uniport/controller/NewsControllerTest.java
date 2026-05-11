package com.uniport.controller;

import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.dto.NewsListResponseDTO;
import com.uniport.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsControllerTest {

    @Test
    void getNewsList_returnsFeaturedAndItems() throws Exception {
        NewsService newsService = mock(NewsService.class);
        when(newsService.getNewsList("MARKET", 0, 20)).thenReturn(NewsListResponseDTO.builder()
                .featured(item("news_001", "MARKET", "시황", true))
                .items(List.of())
                .page(0)
                .size(20)
                .hasNext(false)
                .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService)).build();

        mockMvc.perform(get("/api/news").param("category", "MARKET").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured.id").value("news_001"))
                .andExpect(jsonPath("$.featured.categoryLabel").value("시황"))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(newsService).getNewsList("MARKET", 0, 20);
    }

    @Test
    void getNewsDetail_returnsNewsBody() throws Exception {
        NewsService newsService = mock(NewsService.class);
        when(newsService.getNewsDetail("news_001")).thenReturn(item("news_001", "MARKET", "시황", true));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService)).build();

        mockMvc.perform(get("/api/news/news_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("news_001"))
                .andExpect(jsonPath("$.body").value("본문"));

        verify(newsService).getNewsDetail("news_001");
    }

    private NewsItemResponseDTO item(String id, String category, String categoryLabel, boolean featured) {
        return NewsItemResponseDTO.builder()
                .id(id)
                .category(category)
                .categoryLabel(categoryLabel)
                .title("제목")
                .summary("요약")
                .body("본문")
                .sourceName("UniPort Markets")
                .publishedAt("2026-05-11T11:48:00+09:00")
                .isFeatured(featured)
                .thumbnailUrl(null)
                .externalUrl(null)
                .build();
    }
}
