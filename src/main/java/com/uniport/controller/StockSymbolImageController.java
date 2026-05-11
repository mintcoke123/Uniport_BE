package com.uniport.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

@RestController
public class StockSymbolImageController {

    private static final MediaType SVG_MEDIA_TYPE = MediaType.valueOf("image/svg+xml");
    private static final String DEFAULT_BG = "#EEF2FF";
    private static final String DEFAULT_FG = "#4F46E5";

    @GetMapping(value = "/api/stock-symbols/{market}/{symbol}.svg", produces = "image/svg+xml")
    public ResponseEntity<String> renderSymbol(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "bg", required = false) String bg,
            @RequestParam(value = "fg", required = false) String fg) {
        String label = sanitizeText(text, symbol);
        String bgColor = sanitizeHexColor(bg, DEFAULT_BG);
        String fgColor = sanitizeHexColor(fg, DEFAULT_FG);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" role="img" aria-label="%s %s">
                  <rect width="64" height="64" rx="18" fill="%s"/>
                  <text x="32" y="38" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="18" font-weight="800" fill="%s">%s</text>
                </svg>
                """.formatted(escapeXml(market), escapeXml(symbol), bgColor, fgColor, escapeXml(label));
        return ResponseEntity.ok()
                .contentType(SVG_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(svg);
    }

    private String sanitizeText(String text, String fallback) {
        String value = hasText(text) ? text : fallback;
        value = value == null ? "" : value.trim().replaceAll("\\p{Cntrl}", "");
        if (value.isBlank()) {
            return "?";
        }
        return value.length() <= 5 ? value : value.substring(0, 5);
    }

    private String sanitizeHexColor(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().replace("#", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{6}")) {
            return fallback;
        }
        return "#" + normalized;
    }

    private String escapeXml(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
