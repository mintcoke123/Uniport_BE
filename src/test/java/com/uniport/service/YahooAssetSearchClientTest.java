package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YahooAssetSearchClientTest {

    @Test
    void searchUsEquities_returnsUsEquityMatchesWithUserAgent() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        YahooAssetSearchClient client = new YahooAssetSearchClient(
                restTemplate,
                "https://query1.finance.yahoo.com"
        );
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "quotes": [
                            {"quoteType":"EQUITY","symbol":"AAPL","longname":"Apple Inc.","shortname":"Apple Inc.","exchange":"NMS","exchDisp":"NASDAQ"},
                            {"quoteType":"ETF","symbol":"APLE.TO","longname":"Harvest Apple ETF","exchange":"TOR"},
                            {"quoteType":"EQUITY","symbol":"7203.T","longname":"Toyota Motor Corporation","exchange":"JPX"}
                          ]
                        }
                        """));

        List<YahooAssetSearchClient.YahooAssetResult> results = client.searchUsEquities("apple", 5);

        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).symbol());
        assertEquals("Apple Inc.", results.get(0).name());
        assertEquals("NASDAQ", results.get(0).market());
        URI uri = capturedUri(restTemplate);
        assertTrue(uri.toString().contains("/v1/finance/search?q=apple"));
        assertTrue(capturedRequest(restTemplate).getHeaders().getFirst("User-Agent").contains("Mozilla"));
    }

    @Test
    void searchUsEquities_mapsIrenNasdaqResult() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        YahooAssetSearchClient client = new YahooAssetSearchClient(
                restTemplate,
                "https://query1.finance.yahoo.com"
        );
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"quotes":[{"quoteType":"EQUITY","symbol":"IREN","longname":"IREN Limited","exchange":"NMS","exchDisp":"NASDAQ"}]}
                        """));

        List<YahooAssetSearchClient.YahooAssetResult> results = client.searchUsEquities("iren", 5);

        assertEquals(1, results.size());
        assertEquals("IREN", results.get(0).symbol());
        assertEquals("IREN Limited", results.get(0).name());
        assertEquals("NASDAQ", results.get(0).market());
    }

    @Test
    void searchUsEquities_includesUsEtfMatches() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        YahooAssetSearchClient client = new YahooAssetSearchClient(
                restTemplate,
                "https://query1.finance.yahoo.com"
        );
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"quotes":[{"quoteType":"ETF","symbol":"SOXX","longname":"iShares Semiconductor ETF","exchange":"NMS","exchDisp":"NASDAQ"}]}
                        """));

        List<YahooAssetSearchClient.YahooAssetResult> results = client.searchUsEquities("soxx", 5);

        assertEquals(1, results.size());
        assertEquals("SOXX", results.get(0).symbol());
        assertEquals("iShares Semiconductor ETF", results.get(0).name());
        assertEquals("NASDAQ", results.get(0).market());
    }

    private URI capturedUri(RestTemplate restTemplate) {
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).exchange(captor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        return captor.getValue();
    }

    private HttpEntity<?> capturedRequest(RestTemplate restTemplate) {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), captor.capture(), eq(String.class));
        return captor.getValue();
    }
}
