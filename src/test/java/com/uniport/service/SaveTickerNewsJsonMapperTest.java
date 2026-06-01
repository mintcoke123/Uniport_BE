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
    void extract_readsSaveTickerNewsListWithoutCopyingFullBody() {
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
                      "content": "지멘스가 엔비디아, 플루언스 등 파트너사들과 협력하여 엔비디아의 차세대 AI 팩토리용 레퍼런스 아키텍처를 개발했다는 긴 본문입니다. 이 문장은 원문 전문처럼 길게 이어지므로 그대로 복사되면 안 됩니다.",
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
        assertEquals("정보 $FLNC FLNC $NVDA NVDA", nvidia.getContent());
        assertFalse(nvidia.getContent().contains("그대로 복사되면 안 됩니다."));
        assertEquals("세이브티커", nvidia.getSourceName());
        assertTrue(nvidia.isFeatured());
        assertEquals("https://www.saveticker.com/news/137326", nvidia.getExternalUrl());

        FetchedNewsArticle ism = articles.get(1);
        assertEquals("Financial Juice", ism.getSourceName());
        assertEquals("제조업 물가 지표가 예상치를 밑돌며 금리 기대에 영향을 줄 수 있어요.", ism.getSummary());
        assertEquals(LocalDateTime.of(2026, 6, 1, 23, 1, 7, 217353000), ism.getPublishedAt());
        assertEquals("https://www.saveticker.com/news/137365?groupId=137362", ism.getExternalUrl());
    }
}
