package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveTickerNewsJsonMapperTest {

    private final SaveTickerNewsJsonMapper mapper = new SaveTickerNewsJsonMapper();

    @Test
    void extract_readsSaveTickerNewsListWithoutTrimmingSummary() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.OVERSEAS_STOCK,
                "세이브티커",
                20
        );
        String json = """
                {
                  "news_list": [
                    {
                      "id": "137326",
                      "title": "Siemens, NVIDIA and Fluence develop AI data center architecture",
                      "content": "지멘스가 엔비디아, 플루언스 등 파트너사들과 협력하여 엔비디아의 차세대 AI 팩토리용 레퍼런스 아키텍처를 개발했다는 긴 본문입니다. 이 문장은 투자자가 흐름을 이해할 수 있도록 뒤쪽 문장까지 반드시 유지되어야 합니다. 공급망 협력, 냉각 인프라, 데이터센터 전력 수요까지 연결되는 상세 설명입니다.",
                      "source": null,
                      "created_at": "2026-06-01T22:02:02.263327+09:00",
                      "tag_names": ["정보", "$FLNC", "$NVDA"],
                      "is_top_story": true,
                      "translations": null,
                      "news_group_id": null,
                      "group_summary": null
                    },
                    {
                      "id": "137365",
                      "title": "미국 ISM 제조업 물가 지불 가격 실제 82.1 (예상 85, 이전 84.6)",
                      "content": "",
                      "source": "financial-juice",
                      "created_at": "2026-06-01T23:01:07.217353+09:00",
                      "tag_names": ["속보"],
                      "is_top_story": false,
                      "translations": {
                        "source_locale": "en_US",
                        "translated": {
                          "ko_KR": {
                            "title": "미국 ISM 제조업 물가 지불 가격 실제 82.1 (예상 85, 이전 84.6)",
                            "content": [{"type": "text", "content": ""}],
                            "summary": [{"type": "text", "content": "제조업 물가 지표가 예상치를 밑돌며 금리 기대에 영향을 줄 수 있어요."}]
                          }
                        }
                      },
                      "extra": {
                        "source_url": "https://www.financialjuice.com/example"
                      },
                      "news_group_id": 137362,
                      "group_summary": null
                    }
                  ]
                }
                """;

        List<FetchedNewsArticle> articles = mapper.extract(source, json);

        assertEquals(2, articles.size());
        FetchedNewsArticle nvidia = articles.get(0);
        assertEquals("saveticker_137326", nvidia.getId());
        assertEquals("Siemens, NVIDIA and Fluence develop AI data center architecture", nvidia.getTitle());
        assertTrue(nvidia.getSummary().contains("엔비디아"));
        assertTrue(nvidia.getSummary().contains("데이터센터 전력 수요까지 연결되는 상세 설명입니다."));
        assertEquals("정보 $FLNC FLNC $NVDA NVDA", nvidia.getContent());
        assertFalse(nvidia.getContent().contains("데이터센터 전력 수요까지 연결되는 상세 설명입니다."));
        assertEquals("세이브티커", nvidia.getSourceName());
        assertTrue(nvidia.isFeatured());
        assertEquals("https://www.saveticker.com/news/137326", nvidia.getExternalUrl());

        FetchedNewsArticle ism = articles.get(1);
        assertEquals("Financial Juice", ism.getSourceName());
        assertEquals("제조업 물가 지표가 예상치를 밑돌며 금리 기대에 영향을 줄 수 있어요.", ism.getSummary());
        assertEquals(LocalDateTime.of(2026, 6, 1, 23, 1, 7, 217353000), ism.getPublishedAt());
        assertEquals("https://www.saveticker.com/news/137365?groupId=137362", ism.getExternalUrl());
    }

    @Test
    void enrichWithDetail_replacesSummaryWithTranslatedFullContent() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.OVERSEAS_STOCK,
                "세이브티커",
                20
        );
        FetchedNewsArticle article = FetchedNewsArticle.builder()
                .id("saveticker_139026")
                .category(NewsCategory.OVERSEAS_STOCK)
                .title("시장조사기관 알파센스, 기업가치 상승")
                .summary("짧은 목록 요약")
                .content("AI $NVDA NVDA")
                .sourceName("세이브티커")
                .publishedAt(LocalDateTime.of(2026, 6, 3, 20, 3))
                .featured(false)
                .externalUrl("https://www.saveticker.com/news/139026")
                .build();
        String detailJson = """
                {
                  "news": {
                    "id": "139026",
                    "title": "AlphaSense nearly doubles valuation",
                    "source": "reuters",
                    "created_at": "2026-06-03T20:03:19.021317+09:00",
                    "translations": {
                      "translated": {
                        "ko_KR": {
                          "title": "시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 75억 달러로 거의 두 배 상승",
                          "summary": [{"type": "text", "content": "시장 분석 기업 알파센스, 75억 달러 가치로 신규 자금 조달"}],
                          "content": [
                            {"type": "text", "content": "6월 3일 (로이터) - 시장 인텔리전스 플랫폼 알파센스는 수요일 3억 5천만 달러를 조달했다고 밝혔다."},
                            {"type": "text", "content": "\\n"},
                            {"type": "text", "content": "이번 라운드는 비트루비안 파트너스, 액센츄어 벤처스, J.P. 모건 자산운용이 주도했다."},
                            {"type": "text", "content": "\\n"},
                            {"type": "text", "content": "회사는 신규 투자가 국제 확장과 글로벌 고객 지원 인프라 확장을 지원할 것이라고 밝혔다."}
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        FetchedNewsArticle enriched = mapper.enrichWithDetail(source, article, detailJson);

        assertEquals("시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 75억 달러로 거의 두 배 상승", enriched.getTitle());
        assertTrue(enriched.getSummary().contains("시장 인텔리전스 플랫폼 알파센스는 수요일 3억 5천만 달러를 조달"));
        assertTrue(enriched.getSummary().contains("글로벌 고객 지원 인프라 확장을 지원할 것이라고 밝혔다."));
        assertFalse(enriched.getSummary().contains("짧은 목록 요약"));
        assertEquals("AI $NVDA NVDA", enriched.getContent());
        assertEquals("Reuters", enriched.getSourceName());
    }
}
