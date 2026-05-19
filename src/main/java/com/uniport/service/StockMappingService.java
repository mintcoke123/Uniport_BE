package com.uniport.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class StockMappingService {

    public static final String MATCH_TYPE_DIRECT = "DIRECT";
    public static final String MATCH_TYPE_THEME_CANDIDATE = "THEME_CANDIDATE";

    private static final List<StockDefinition> DIRECT_STOCKS = List.of(
            new StockDefinition("삼성전자", "005930", "KOSPI", List.of("삼성전자")),
            new StockDefinition("SK하이닉스", "000660", "KOSPI", List.of("하이닉스", "SK하이닉스")),
            new StockDefinition("LG에너지솔루션", "373220", "KOSPI", List.of("LG엔솔", "LG에너지솔루션")),
            new StockDefinition("NAVER", "035420", "KOSPI", List.of("네이버", "NAVER")),
            new StockDefinition("S-OIL", "010950", "KOSPI", List.of("에쓰오일", "S-OIL")),
            new StockDefinition("Tesla", "TSLA", "NASDAQ", List.of("테슬라", "Tesla")),
            new StockDefinition("NVIDIA", "NVDA", "NASDAQ", List.of("엔비디아", "NVIDIA")),
            new StockDefinition("Apple", "AAPL", "NASDAQ", List.of("애플", "Apple"))
    );

    private static final List<ThemeDefinition> THEME_STOCKS = List.of(
            new ThemeDefinition(
                    List.of("HBM", "AI반도체", "AI 반도체", "반도체"),
                    List.of(
                            new StockDefinition("삼성전자", "005930", "KOSPI"),
                            new StockDefinition("SK하이닉스", "000660", "KOSPI"),
                            new StockDefinition("한미반도체", "042700", "KOSPI")
                    )
            ),
            new ThemeDefinition(
                    List.of("2차전지", "배터리", "전기차"),
                    List.of(
                            new StockDefinition("LG에너지솔루션", "373220", "KOSPI"),
                            new StockDefinition("삼성SDI", "006400", "KOSPI"),
                            new StockDefinition("에코프로비엠", "086520", "KOSDAQ")
                    )
            ),
            new ThemeDefinition(
                    List.of("방산"),
                    List.of(
                            new StockDefinition("한화에어로스페이스", "012450", "KOSPI"),
                            new StockDefinition("LIG넥스원", "079550", "KOSPI"),
                            new StockDefinition("현대로템", "064350", "KOSPI")
                    )
            ),
            new ThemeDefinition(
                    List.of("원전"),
                    List.of(
                            new StockDefinition("두산에너빌리티", "034020", "KOSPI"),
                            new StockDefinition("한전기술", "052690", "KOSPI")
                    )
            ),
            new ThemeDefinition(
                    List.of("로봇"),
                    List.of(
                            new StockDefinition("두산로보틱스", "454910", "KOSPI"),
                            new StockDefinition("레인보우로보틱스", "277810", "KOSDAQ")
                    )
            )
    );

    public List<MappedStock> mapStocks(String... texts) {
        String text = searchableText(texts);
        if (text.isBlank()) {
            return List.of();
        }

        List<MappedStock> mappedStocks = new ArrayList<>();
        Set<String> seenSymbols = new LinkedHashSet<>();

        for (DirectMatch match : directMatches(text)) {
            if (seenSymbols.add(match.stock().symbol())) {
                mappedStocks.add(match.stock().toMappedStock(MATCH_TYPE_DIRECT));
            }
        }

        for (ThemeMatch match : themeMatches(text)) {
            for (StockDefinition stock : match.theme().stocks()) {
                if (seenSymbols.add(stock.symbol())) {
                    mappedStocks.add(stock.toMappedStock(MATCH_TYPE_THEME_CANDIDATE));
                }
            }
        }

        return List.copyOf(mappedStocks);
    }

    private List<DirectMatch> directMatches(String text) {
        List<DirectMatch> matches = new ArrayList<>();
        for (int order = 0; order < DIRECT_STOCKS.size(); order++) {
            StockDefinition stock = DIRECT_STOCKS.get(order);
            int firstIndex = firstAliasIndex(text, stock.aliases());
            if (firstIndex >= 0) {
                matches.add(new DirectMatch(firstIndex, order, stock));
            }
        }
        matches.sort(Comparator
                .comparingInt(DirectMatch::index)
                .thenComparingInt(DirectMatch::order));
        return matches;
    }

    private List<ThemeMatch> themeMatches(String text) {
        List<ThemeMatch> matches = new ArrayList<>();
        for (int order = 0; order < THEME_STOCKS.size(); order++) {
            ThemeDefinition theme = THEME_STOCKS.get(order);
            int firstIndex = firstAliasIndex(text, theme.aliases());
            if (firstIndex >= 0) {
                matches.add(new ThemeMatch(firstIndex, order, theme));
            }
        }
        matches.sort(Comparator
                .comparingInt(ThemeMatch::index)
                .thenComparingInt(ThemeMatch::order));
        return matches;
    }

    private int firstAliasIndex(String text, List<String> aliases) {
        int firstIndex = -1;
        for (String alias : aliases) {
            int index = firstAliasIndex(text, alias);
            if (index >= 0 && (firstIndex < 0 || index < firstIndex)) {
                firstIndex = index;
            }
        }
        return firstIndex;
    }

    private int firstAliasIndex(String text, String alias) {
        String normalizedAlias = normalize(alias);
        if (normalizedAlias.isBlank()) {
            return -1;
        }
        if (!containsEnglishLetter(normalizedAlias)) {
            return text.indexOf(normalizedAlias);
        }

        int searchFrom = 0;
        while (searchFrom <= text.length() - normalizedAlias.length()) {
            int index = text.indexOf(normalizedAlias, searchFrom);
            if (index < 0) {
                return -1;
            }
            if (hasEnglishTokenBoundaries(text, index, normalizedAlias.length())) {
                return index;
            }
            searchFrom = index + 1;
        }
        return -1;
    }

    private boolean hasEnglishTokenBoundaries(String text, int index, int length) {
        int before = index - 1;
        int after = index + length;
        return (before < 0 || !isEnglishTokenCharacter(text.charAt(before)))
                && (after >= text.length() || !isEnglishTokenCharacter(text.charAt(after)));
    }

    private boolean containsEnglishLetter(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                return true;
            }
        }
        return false;
    }

    private boolean isEnglishTokenCharacter(char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    private String searchableText(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }
        List<String> presentTexts = new ArrayList<>();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                presentTexts.add(text);
            }
        }
        return normalize(String.join(" ", presentTexts));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private record StockDefinition(String name, String symbol, String market, List<String> aliases) {

        private StockDefinition(String name, String symbol, String market) {
            this(name, symbol, market, List.of(name));
        }

        private MappedStock toMappedStock(String matchType) {
            return new MappedStock(name, symbol, market, matchType);
        }
    }

    private record ThemeDefinition(List<String> aliases, List<StockDefinition> stocks) {
    }

    private record DirectMatch(int index, int order, StockDefinition stock) {
    }

    private record ThemeMatch(int index, int order, ThemeDefinition theme) {
    }
}
