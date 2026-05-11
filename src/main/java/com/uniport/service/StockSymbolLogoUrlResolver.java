package com.uniport.service;

import com.uniport.dto.StockVisualDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class StockSymbolLogoUrlResolver {

    private static final String DEFAULT_BASE_URL = "https://uniportbe-production.up.railway.app";

    private final String publicBaseUrl;

    public StockSymbolLogoUrlResolver(
            @Value("${app.public-base-url:https://uniportbe-production.up.railway.app}") String publicBaseUrl) {
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public String resolve(String market, String symbol, StockVisualDTO visual) {
        String normalizedSymbol = pathSegment(symbol);
        if (normalizedSymbol.isBlank() || visual == null) {
            return null;
        }
        String normalizedMarket = pathSegment(market);
        if (normalizedMarket.isBlank()) {
            normalizedMarket = "STOCK";
        }
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment("api", "stock-symbols", normalizedMarket, normalizedSymbol + ".svg")
                .queryParam("text", firstNonBlank(visual.getText(), normalizedSymbol))
                .queryParam("bg", stripHash(visual.getBgColor()))
                .queryParam("fg", stripHash(visual.getTextColor()))
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String pathSegment(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "");
    }

    private String stripHash(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.startsWith("#") ? normalized.substring(1) : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
