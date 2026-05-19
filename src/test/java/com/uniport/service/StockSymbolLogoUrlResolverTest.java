package com.uniport.service;

import com.uniport.dto.StockVisualDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class StockSymbolLogoUrlResolverTest {

    @Test
    void resolve_doesNotCreateLogoUrlFromFallbackVisual() {
        StockSymbolLogoUrlResolver resolver = new StockSymbolLogoUrlResolver(
                "https://uniportbe-production.up.railway.app"
        );
        StockVisualDTO visual = StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text("엔비")
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();

        String logoUrl = resolver.resolve("US", "NVDA", visual);

        assertNull(logoUrl);
    }
}
