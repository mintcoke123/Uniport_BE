package com.uniport.service;

import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.StockMaster;
import com.uniport.repository.StockMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockVisualAssetResolverTest {

    private StockMasterRepository stockMasterRepository;
    private StockVisualAssetResolver resolver;

    @BeforeEach
    void setUp() {
        stockMasterRepository = mock(StockMasterRepository.class);
        resolver = new StockVisualAssetResolver(stockMasterRepository);
    }

    @Test
    void resolve_usesMasterNameWhenReceivedNameDoesNotMatch() {
        StockMaster master = stock("373220", "LG에너지솔루션", "KOSPI");
        when(stockMasterRepository.findById("373220")).thenReturn(Optional.of(master));

        StockVisualDTO visual = resolver.resolve("KRX", "373220", "삼성전자", null);

        assertEquals("FALLBACK_SYMBOL", visual.getType());
        assertEquals("LG", visual.getText());
        assertNotNull(visual.getBgColor());
        assertNotNull(visual.getTextColor());
    }

    @Test
    void resolve_usesFallbackSymbolEvenWhenLogoUrlExists() {
        StockMaster master = stock("005930", "삼성전자", "KOSPI");
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));

        StockVisualDTO visual = resolver.resolve("KOSPI", "005930", "삼성전자", "https://cdn.uniport.kr/stocks/KRX_005930.png");

        assertEquals("FALLBACK_SYMBOL", visual.getType());
        assertEquals("삼성", visual.getText());
    }

    @Test
    void resolve_removesPreferredShareSuffixBeforeKoreanBrandSymbol() {
        when(stockMasterRepository.findById("005935")).thenReturn(Optional.empty());

        StockVisualDTO visual = resolver.resolve("KRX", "005935", "삼성전자우", null);

        assertEquals("FALLBACK_SYMBOL", visual.getType());
        assertEquals("삼성", visual.getText());
    }

    @Test
    void resolve_matchesPoscoPrefixAndUsesDeterministicColor() {
        StockMaster master = stock("005490", "POSCO홀딩스", "KOSPI");
        when(stockMasterRepository.findById("005490")).thenReturn(Optional.of(master));

        StockVisualDTO first = resolver.resolve("KRX", "005490", "POSCO홀딩스", null);
        StockVisualDTO second = resolver.resolve("KOSPI", "005490", "POSCO홀딩스", null);

        assertEquals("POSCO", first.getText());
        assertEquals(first.getBgColor(), second.getBgColor());
        assertEquals(first.getTextColor(), second.getTextColor());
    }

    @Test
    void resolve_unknownWithoutNameUsesQuestionMarkAndNeutralColor() {
        when(stockMasterRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        StockVisualDTO visual = resolver.resolve("KRX", "UNKNOWN", "", null);

        assertEquals("?", visual.getText());
        assertEquals("#E5E7EB", visual.getBgColor());
        assertEquals("#374151", visual.getTextColor());
    }

    private StockMaster stock(String code, String name, String market) {
        return StockMaster.builder()
                .code(code)
                .nameKr(name)
                .market(market)
                .build();
    }
}
