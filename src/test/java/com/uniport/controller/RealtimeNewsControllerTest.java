package com.uniport.controller;

import com.uniport.dto.RealtimeNewsCategoryDTO;
import com.uniport.dto.RealtimeNewsDetailResponseDTO;
import com.uniport.dto.RealtimeNewsItemDTO;
import com.uniport.dto.RealtimeNewsListResponseDTO;
import com.uniport.dto.RealtimeNewsRelatedStockDTO;
import com.uniport.dto.RealtimeNewsSourceArticleDTO;
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

class RealtimeNewsControllerTest {

    @Test
    void getRealtimeNewsList_returnsFrontendContract() throws Exception {
        NewsService newsService = mock(NewsService.class);
        when(newsService.getRealtimeNewsList("EARNINGS", "cursor_1", 20)).thenReturn(
                RealtimeNewsListResponseDTO.builder()
                        .categories(List.of(
                                RealtimeNewsCategoryDTO.builder().category("ALL").label("전체").build(),
                                RealtimeNewsCategoryDTO.builder().category("EARNINGS").label("실적").build()
                        ))
                        .selectedCategory("EARNINGS")
                        .heroNews(realtimeItem("NEWS_001"))
                        .items(List.of(realtimeItem("NEWS_002")))
                        .nextCursor("NEWS_002")
                        .hasNext(true)
                        .build()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RealtimeNewsController(newsService)).build();

        mockMvc.perform(get("/api/mock-investing/realtime-news")
                        .param("category", "EARNINGS")
                        .param("cursor", "cursor_1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[1].category").value("EARNINGS"))
                .andExpect(jsonPath("$.selectedCategory").value("EARNINGS"))
                .andExpect(jsonPath("$.heroNews.newsId").value("NEWS_001"))
                .andExpect(jsonPath("$.heroNews.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.heroNews.sentimentLabel").value("호재"))
                .andExpect(jsonPath("$.heroNews.sentimentReason").value("FinBERT가 금융 문맥상 긍정 신호로 분류했어요."))
                .andExpect(jsonPath("$.heroNews.investmentPoints[0]").value("실적 기대가 투자 심리에 영향을 줄 수 있어요."))
                .andExpect(jsonPath("$.heroNews.riskPoints[0]").value("기대감 선반영 이후 변동성이 커질 수 있어요."))
                .andExpect(jsonPath("$.items[0].relatedStocks[0]").value("삼성전자"))
                .andExpect(jsonPath("$.nextCursor").value("NEWS_002"))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(newsService).getRealtimeNewsList("EARNINGS", "cursor_1", 20);
    }

    @Test
    void getRealtimeNewsDetail_returnsPointBasedDetailWithoutBodyRequirement() throws Exception {
        NewsService newsService = mock(NewsService.class);
        when(newsService.getRealtimeNewsDetail("NEWS_001")).thenReturn(
                RealtimeNewsDetailResponseDTO.builder()
                        .newsId("NEWS_001")
                        .category("COMPANY")
                        .categoryLabel("종목")
                        .title("삼성전자 반등")
                        .summary("반도체 투자 심리가 회복되고 있어요.")
                        .body("삼성전자 반등은 반도체 업황 기대와 함께 봐야 해요.")
                        .sourceName("한국경제")
                        .publishedAt("2026-05-11T16:10:00+09:00")
                        .externalUrl("https://www.hankyung.com/example")
                        .coreSummary("삼성전자 반등은 반도체 업황 기대와 함께 봐야 해요.")
                        .sentiment("POSITIVE")
                        .sentimentLabel("호재")
                        .sentimentScore(0.91)
                        .sentimentReason("FinBERT가 금융 문맥상 긍정 신호로 분류했어요.")
                        .investmentPoints(List.of("반도체 업황 기대가 주가 반등 재료로 언급되고 있어요."))
                        .riskPoints(List.of("단기 반등 이후 차익실현 매물이 나올 수 있어요."))
                        .relatedStocks(List.of(RealtimeNewsRelatedStockDTO.builder()
                                .stockId("KR_005930")
                                .name("삼성전자")
                                .symbol("005930")
                                .market("KOSPI")
                                .build()))
                        .sourceArticles(List.of(RealtimeNewsSourceArticleDTO.builder()
                                .articleId("ARTICLE_NEWS_001")
                                .sourceName("한국경제")
                                .title("삼성전자 반등")
                                .summary("반도체 투자 심리가 회복되고 있어요.")
                                .publishedAt("2026-05-11T16:10:00+09:00")
                                .externalUrl("https://www.hankyung.com/example")
                                .build()))
                        .build()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RealtimeNewsController(newsService)).build();

        mockMvc.perform(get("/api/mock-investing/realtime-news/NEWS_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsId").value("NEWS_001"))
                .andExpect(jsonPath("$.body").value("삼성전자 반등은 반도체 업황 기대와 함께 봐야 해요."))
                .andExpect(jsonPath("$.coreSummary").value("삼성전자 반등은 반도체 업황 기대와 함께 봐야 해요."))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.sentimentLabel").value("호재"))
                .andExpect(jsonPath("$.sentimentReason").value("FinBERT가 금융 문맥상 긍정 신호로 분류했어요."))
                .andExpect(jsonPath("$.investmentPoints[0]").value("반도체 업황 기대가 주가 반등 재료로 언급되고 있어요."))
                .andExpect(jsonPath("$.riskPoints[0]").value("단기 반등 이후 차익실현 매물이 나올 수 있어요."))
                .andExpect(jsonPath("$.relatedStocks[0].symbol").value("005930"))
                .andExpect(jsonPath("$.sourceArticles[0].sourceName").value("한국경제"));

        verify(newsService).getRealtimeNewsDetail("NEWS_001");
    }

    private RealtimeNewsItemDTO realtimeItem(String newsId) {
        return RealtimeNewsItemDTO.builder()
                .newsId(newsId)
                .category("EARNINGS")
                .categoryLabel("실적")
                .title("삼성전자 실적 기대")
                .summary("반도체 실적 기대가 커지고 있어요.")
                .sourceName("한국경제")
                .publishedAt("2026-05-11T16:10:00+09:00")
                .externalUrl("https://www.hankyung.com/example")
                .sentiment("POSITIVE")
                .sentimentLabel("호재")
                .sentimentScore(0.91)
                .sentimentReason("FinBERT가 금융 문맥상 긍정 신호로 분류했어요.")
                .relatedStocks(List.of("삼성전자"))
                .investmentPoints(List.of("실적 기대가 투자 심리에 영향을 줄 수 있어요."))
                .riskPoints(List.of("기대감 선반영 이후 변동성이 커질 수 있어요."))
                .build();
    }
}
