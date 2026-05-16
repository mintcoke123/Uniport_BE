package com.uniport.service;

import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.service.kisws.PriceCache;
import com.uniport.service.kisws.PriceSnapshot;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KisApiServiceTest {

    @Test
    void getStockQuote_mapsOhlcFromKisHttpQuoteEvenWhenRealtimeCacheExists() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        KisWsSubscriptionManager subscriptionManager = mock(KisWsSubscriptionManager.class);
        PriceCache priceCache = new PriceCache();
        priceCache.put("005930", new PriceSnapshot(
                new BigDecimal("70000"),
                new BigDecimal("1000"),
                new BigDecimal("1.45"),
                1000L,
                System.currentTimeMillis()
        ));
        StockVisualAssetResolver visualResolver = mock(StockVisualAssetResolver.class);
        StockSymbolLogoUrlResolver logoUrlResolver = mock(StockSymbolLogoUrlResolver.class);
        StockVisualDTO visual = StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text("삼성")
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
        when(visualResolver.resolve(anyString(), anyString(), anyString(), any())).thenReturn(visual);
        when(logoUrlResolver.resolve(anyString(), anyString(), any())).thenReturn("https://example.com/logo.png");
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenAnswer(invocation -> {
            HttpMethod method = invocation.getArgument(1);
            if (HttpMethod.POST.equals(method)) {
                return ResponseEntity.ok(Map.of(
                        "access_token", "access-token",
                        "expires_in", 3600
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "rt_cd", "0",
                    "output", Map.of(
                            "hts_kor_isnm", "삼성전자",
                            "stck_prpr", "70000",
                            "stck_oprc", "69000",
                            "stck_clpr", "70000",
                            "stck_lwpr", "68500",
                            "stck_hgpr", "71000",
                            "prdy_vrss", "1000",
                            "prdy_ctrt", "1.45",
                            "acml_vol", "12345"
                    )
            ));
        });
        KisApiService service = new KisApiService(
                restTemplate,
                subscriptionManager,
                priceCache,
                null,
                visualResolver,
                logoUrlResolver
        );
        ReflectionTestUtils.setField(service, "baseUrl", "https://kis.example");
        ReflectionTestUtils.setField(service, "appkey", "appkey");
        ReflectionTestUtils.setField(service, "appsecret", "appsecret");

        StockPriceDTO result = service.getStockQuote("005930");

        assertEquals(new BigDecimal("70000"), result.getCurrentPrice());
        assertEquals(new BigDecimal("69000"), result.getOpenPrice());
        assertEquals(new BigDecimal("70000"), result.getClosePrice());
        assertEquals(new BigDecimal("68500"), result.getLowPrice());
        assertEquals(new BigDecimal("71000"), result.getHighPrice());
        assertEquals(12345L, result.getVolume());
    }
}
