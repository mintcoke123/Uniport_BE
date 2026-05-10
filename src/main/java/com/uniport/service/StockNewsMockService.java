package com.uniport.service;

import com.uniport.dto.StockNewsCompanyInfoDTO;
import com.uniport.dto.StockNewsDetailResponseDTO;
import com.uniport.dto.StockNewsListItemDTO;
import com.uniport.dto.StockNewsListResponseDTO;
import com.uniport.dto.StockNewsOpinionDTO;
import com.uniport.dto.StockNewsTagDTO;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockNewsMockService {

    private final List<StockNewsDetailResponseDTO> articles;
    private final StockVisualAssetResolver stockVisualAssetResolver;

    public StockNewsMockService(StockVisualAssetResolver stockVisualAssetResolver) {
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.articles = createArticles();
    }

    public StockNewsListResponseDTO getNewsList(String keyword, String sort, Integer page, Integer size) {
        String normalizedKeyword = keyword != null ? keyword.trim() : "";
        String normalizedSort = normalizeSort(sort);
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 ? Math.min(size, 20) : 10;

        List<StockNewsDetailResponseDTO> filtered = articles.stream()
                .filter(article -> matchesKeyword(article, normalizedKeyword))
                .sorted(resolveComparator(normalizedSort))
                .collect(Collectors.toList());

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        boolean hasNext = toIndex < filtered.size();

        List<StockNewsListItemDTO> items = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toListItem)
                .toList();

        return StockNewsListResponseDTO.builder()
                .items(items)
                .sort(normalizedSort)
                .keyword(normalizedKeyword.isBlank() ? null : normalizedKeyword)
                .page(safePage)
                .size(safeSize)
                .hasNext(hasNext)
                .build();
    }

    public StockNewsDetailResponseDTO getNewsDetail(String newsId) {
        return articles.stream()
                .filter(article -> article.getNewsId().equals(newsId))
                .findFirst()
                .orElseThrow(() -> new ApiException("News article not found", HttpStatus.NOT_FOUND));
    }

    private boolean matchesKeyword(StockNewsDetailResponseDTO article, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        if (article.getTitle() != null && article.getTitle().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return true;
        }
        if (article.getCompany() != null) {
            if (article.getCompany().getStockName() != null
                    && article.getCompany().getStockName().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                return true;
            }
            if (article.getCompany().getStockCode() != null
                    && article.getCompany().getStockCode().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                return true;
            }
        }
        return article.getTags() != null && article.getTags().stream()
                .anyMatch(tag -> tag.getLabel() != null && tag.getLabel().toLowerCase(Locale.ROOT).contains(lowerKeyword));
    }

    private Comparator<StockNewsDetailResponseDTO> resolveComparator(String sort) {
        if ("POPULAR".equals(sort)) {
            return Comparator.comparingInt(this::popularityScore).reversed()
                    .thenComparing(StockNewsDetailResponseDTO::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(StockNewsDetailResponseDTO::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int popularityScore(StockNewsDetailResponseDTO article) {
        return switch (article.getNewsId()) {
            case "NEWS_101" -> 124;
            case "NEWS_102" -> 98;
            case "NEWS_103" -> 88;
            case "NEWS_104" -> 72;
            default -> 50;
        };
    }

    private String normalizeSort(String sort) {
        String value = sort != null ? sort.trim().toUpperCase(Locale.ROOT) : "LATEST";
        return "POPULAR".equals(value) ? "POPULAR" : "LATEST";
    }

    private StockNewsListItemDTO toListItem(StockNewsDetailResponseDTO article) {
        return StockNewsListItemDTO.builder()
                .newsId(article.getNewsId())
                .title(article.getTitle())
                .sourceLabel(article.getSourceLabel())
                .imageUrl(article.getImageUrl())
                .tags(article.getTags())
                .publishedAt(article.getPublishedAt())
                .popularityScore(popularityScore(article))
                .build();
    }

    private List<StockNewsDetailResponseDTO> createArticles() {
        List<StockNewsDetailResponseDTO> list = new ArrayList<>();

        list.add(StockNewsDetailResponseDTO.builder()
                .newsId("NEWS_101")
                .title("삼성전자, 3분기 영업이익 2.4조원 기록... 반도체 부문 흑자 전환 성공적 안착")
                .source("어쩌구경제")
                .sourceLabel("출처 · 일주일 전")
                .publishedAt("2026-03-28T09:00:00Z")
                .tags(List.of(
                        tag("삼성전자", "UP", 39.0),
                        tag("SK하이닉스", "DOWN", 39.0),
                        tag("KOSPI", "UP", 39.0)
                ))
                .aiSummary("반도체 업황 회복에 힘입어 삼성전자가 3분기 흑자 전환에 성공했으며, 향후 HBM 공급 확대로 추가 실적 개선이 기대된다는 분석입니다.")
                .aiOpinion(StockNewsOpinionDTO.builder().label("강력 호재").englishLabel("Bullish").build())
                .bodyParagraphs(List.of(
                        "삼성전자가 올해 3분기 시장의 예상을 뛰어넘는 실적을 발표하며 반도체 불황의 끝을 알렸습니다. 특히 DS(Device Solutions) 부문에서의 흑자 전환이 전체 실적을 견인한 것으로 분석됩니다.",
                        "이번 실적 발표에서 가장 눈에 띄는 점은 메모리 반도체 가격의 상승세와 고대역폭메모리(HBM) 수요의 폭발적인 증가입니다. AI 산업의 발전으로 인해 데이터센터용 고성능 메모리 수요가 급증하면서 삼성전자의 프리미엄 제품군 판매가 크게 늘어났습니다.",
                        "증권가에서는 이번 실적을 기점으로 본격적인 실적 개선 구간에 진입했다고 평가하고 있습니다. 4분기에는 D램 가격 상승폭이 더 커질 것으로 예상되며, 파운드리 부문의 가동률 회복도 기대해볼 만하다는 전망이 나옵니다.",
                        "한편 모바일 경험(MX) 사업부는 갤럭시 Z 시리즈 신제품 출시 효과가 반영되며 견조한 수익성을 유지했습니다. 다만 글로벌 경기 둔화로 인한 가전 수요 혼조는 여전히 숙제로 남아있습니다.",
                        "삼성전자는 다가오는 컨퍼런스 콜에서 구체적인 4분기 전망과 내년도 설비 투자 계획을 발표할 예정입니다. 투자자들은 특히 차세대 HBM 개발 로드맵에 촉각을 곤두세우고 있습니다."
                ))
                .imageUrl("https://images.unsplash.com/photo-1545239351-1141bd82e8a6?auto=format&fit=crop&w=1200&q=80")
                .keyPoints(List.of(
                        "DS부문 3개 분기만에 흑자 전환",
                        "HBM3 등 고부가가치 제품 비중 확대",
                        "모바일 부문 견조한 실적 유지"
                ))
                .company(withCompanyVisual(StockNewsCompanyInfoDTO.builder()
                        .stockName("삼성전자")
                        .stockCode("005930")
                        .market("KRX")
                        .logoUrl(null)
                        .description("대한민국 삼성 그룹의 전자·반도체 제조 기업. 삼성의 계열사들 중 최대 규모의 기업이며 글로벌 시장에서 한국을 대표하는 기업 브랜드로 손꼽힙니다.")
                        .source("어쩌구저쩌구")
                        .stockPricePath("/api/stocks/search?keyword=삼성전자&page=0&size=10")
                        .build()))
                .disclaimer("본 뉴스는 인공지능 알고리즘에 의해 요약 및 분석되었습니다. 투자의 책임은 투자자 본인에게 있습니다.")
                .build());

        list.add(simpleArticle(
                "NEWS_102",
                "삼성전자, 기업 최초로 '시총 1조 클럽'… 부럽다",
                "2026-03-27T09:00:00Z",
                List.of(tag("삼성전자", "UP", 39.0)),
                "시가총액 상승과 외국인 매수세 유입이 이어지며 심리적인 상징선 돌파가 투자심리를 자극하고 있습니다.",
                "호재",
                "Bullish",
                "시가총액 상징선 돌파가 개인과 기관의 동반 매수세를 자극했다는 분석입니다.",
                "대한민국 대표 대형주로서 글로벌 자금 유입의 수혜를 받고 있습니다."
        ));

        list.add(simpleArticle(
                "NEWS_103",
                "SK하이닉스, HBM 공급 확대 기대감에 장중 강세",
                "2026-03-26T02:00:00Z",
                List.of(
                        tag("SK하이닉스", "UP", 18.5),
                        tag("KOSPI", "UP", 2.1)
                ),
                "AI 서버 수요 확대에 따라 HBM 생산능력 증설 기대감이 주가를 끌어올렸습니다.",
                "긍정적",
                "Positive",
                "HBM 관련 수주 기대감이 단기적으로 투자심리를 지지하고 있습니다.",
                "메모리 반도체 중심의 글로벌 경쟁력을 보유한 기업입니다."
        ));

        list.add(simpleArticle(
                "NEWS_104",
                "KOSPI, 외국인 순매수에 2,800선 재도전",
                "2026-03-25T06:00:00Z",
                List.of(
                        tag("KOSPI", "UP", 1.7),
                        tag("삼성전자", "UP", 2.3)
                ),
                "대형주 중심의 수급 개선이 지수 반등을 이끌고 있습니다.",
                "중립 이상",
                "Neutral+",
                "지수 반등 흐름은 긍정적이지만 업종별 차별화는 이어질 가능성이 있습니다.",
                "국내 대표 지수로 대형주 수급의 영향을 크게 받습니다."
        ));

        return list;
    }

    private StockNewsDetailResponseDTO simpleArticle(String newsId,
                                                     String title,
                                                     String publishedAt,
                                                     List<StockNewsTagDTO> tags,
                                                     String aiSummary,
                                                     String opinionLabel,
                                                     String opinionEnglish,
                                                     String paragraph,
                                                     String companyDescription) {
        String stockName = tags.isEmpty() ? "시장" : tags.get(0).getLabel();
        String stockCode = switch (stockName) {
            case "삼성전자" -> "005930";
            case "SK하이닉스" -> "000660";
            default -> "KOSPI";
        };
        return StockNewsDetailResponseDTO.builder()
                .newsId(newsId)
                .title(title)
                .source("어쩌구경제")
                .sourceLabel("출처 · 일주일 전")
                .publishedAt(publishedAt)
                .tags(tags)
                .aiSummary(aiSummary)
                .aiOpinion(StockNewsOpinionDTO.builder().label(opinionLabel).englishLabel(opinionEnglish).build())
                .bodyParagraphs(List.of(
                        paragraph,
                        "시장 참여자들은 관련 산업의 실적 개선 가능성과 수급 흐름을 함께 체크하고 있습니다."
                ))
                .imageUrl("https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?auto=format&fit=crop&w=1200&q=80")
                .keyPoints(List.of(
                        "최근 수급 흐름 개선",
                        "관련 업종 기대감 확대",
                        "단기 변동성은 여전히 존재"
                ))
                .company(withCompanyVisual(StockNewsCompanyInfoDTO.builder()
                        .stockName(stockName)
                        .stockCode(stockCode)
                        .market("KRX")
                        .logoUrl(null)
                        .description(companyDescription)
                        .source("어쩌구저쩌구")
                        .stockPricePath("/api/stocks/search?keyword=" + stockName + "&page=0&size=10")
                        .build()))
                .disclaimer("본 뉴스는 인공지능 알고리즘에 의해 요약 및 분석되었습니다. 투자의 책임은 투자자 본인에게 있습니다.")
                .build();
    }

    private StockNewsTagDTO tag(String label, String direction, double changeRate) {
        return StockNewsTagDTO.builder()
                .label(label)
                .market("KRX")
                .logoUrl(null)
                .visual(stockVisualAssetResolver.resolve("KRX", null, label, null))
                .direction(direction)
                .changeRate(changeRate)
                .build();
    }

    private StockNewsCompanyInfoDTO withCompanyVisual(StockNewsCompanyInfoDTO company) {
        company.setVisual(stockVisualAssetResolver.resolve(company.getMarket(), company.getStockCode(), company.getStockName(), null));
        return company;
    }
}
