package com.uniport.service.importer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NasdaqSymbolDirectoryParser {

    public List<UsAssetMasterRow> parseNasdaqListed(String text) {
        List<UsAssetMasterRow> rows = new ArrayList<>();
        for (String line : lines(text)) {
            String[] columns = split(line);
            if (columns.length < 7 || isHeader(columns[0], "Symbol") || isFooter(columns[0])) {
                continue;
            }
            String symbol = normalizeSymbol(columns[0]);
            String name = columns[1].trim();
            String testIssue = columns[3].trim();
            String etf = columns[6].trim();
            if (symbol == null || name.isBlank() || "Y".equalsIgnoreCase(testIssue) || "Y".equalsIgnoreCase(etf)) {
                continue;
            }
            rows.add(new UsAssetMasterRow("US_" + symbol, name, symbol, "NASDAQ"));
        }
        return dedupe(rows);
    }

    public List<UsAssetMasterRow> parseOtherListed(String text) {
        List<UsAssetMasterRow> rows = new ArrayList<>();
        for (String line : lines(text)) {
            String[] columns = split(line);
            if (columns.length < 8 || isHeader(columns[0], "ACT Symbol") || isFooter(columns[0])) {
                continue;
            }
            String symbol = normalizeSymbol(columns[7]);
            String name = columns[1].trim();
            String market = mapExchange(columns[2]);
            String etf = columns[4].trim();
            String testIssue = columns[6].trim();
            if (symbol == null || name.isBlank() || market == null
                    || "Y".equalsIgnoreCase(testIssue) || "Y".equalsIgnoreCase(etf)) {
                continue;
            }
            rows.add(new UsAssetMasterRow("US_" + symbol, name, symbol, market));
        }
        return dedupe(rows);
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String[] split(String line) {
        return line.split("\\|", -1);
    }

    private boolean isHeader(String value, String expected) {
        return expected.equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private boolean isFooter(String value) {
        return value != null && value.trim().startsWith("File Creation Time:");
    }

    private String normalizeSymbol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9.\\-+]{0,13}")) {
            return null;
        }
        return normalized;
    }

    private String mapExchange(String value) {
        String exchange = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (exchange) {
            case "N" -> "NYSE";
            case "A" -> "AMEX";
            case "P" -> "NYSE_ARCA";
            case "Z" -> "BATS";
            case "V" -> "IEX";
            default -> null;
        };
    }

    private List<UsAssetMasterRow> dedupe(List<UsAssetMasterRow> rows) {
        Map<String, UsAssetMasterRow> unique = new LinkedHashMap<>();
        for (UsAssetMasterRow row : rows) {
            unique.putIfAbsent(row.assetId(), row);
        }
        return new ArrayList<>(unique.values());
    }
}
