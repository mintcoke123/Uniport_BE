package com.uniport.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StockMappingService {

    public static final String MATCH_TYPE_DIRECT = "DIRECT";
    public static final String MATCH_TYPE_THEME_CANDIDATE = "THEME_CANDIDATE";
    private static final Pattern TELEGRAM_DISCLOSURE_CODE_PATTERN = Pattern.compile(
            "기업명:\\s*([^\\s(]+).*?\\bA?(\\d{6})\\b"
    );

    private static final List<StockDefinition> DIRECT_STOCKS = List.of(
            new StockDefinition("삼성전자", "005930", "KOSPI", List.of("삼성전자")),
            new StockDefinition("SK하이닉스", "000660", "KOSPI", List.of("하이닉스", "SK하이닉스")),
            new StockDefinition("LG에너지솔루션", "373220", "KOSPI", List.of("LG엔솔", "LG에너지솔루션")),
            new StockDefinition("NAVER", "035420", "KOSPI", List.of("네이버", "NAVER")),
            new StockDefinition("S-OIL", "010950", "KOSPI", List.of("에쓰오일", "S-OIL")),
            new StockDefinition("한화에어로스페이스", "012450", "KOSPI", List.of("한화에어로스페이스")),
            new StockDefinition("현대차", "005380", "KOSPI", List.of("현대차", "현대자동차", "Hyundai Motor")),
            new StockDefinition("두산에너빌리티", "034020", "KOSPI", List.of("두산에너빌리티")),
            new StockDefinition("CJ", "001040", "KOSPI", List.of("CJ", "CJ올리브영")),
            new StockDefinition("LG디스플레이", "034220", "KOSPI", List.of("LG디스플레이")),
            new StockDefinition("링네트", "042500", "KOSDAQ", List.of("링네트")),
            new StockDefinition("풍산", "103140", "KOSPI", List.of("풍산")),
            new StockDefinition("올릭스", "226950", "KOSDAQ", List.of("올릭스")),
            new StockDefinition("오스코텍", "039200", "KOSDAQ", List.of("오스코텍")),
            new StockDefinition("이엔에프테크놀로지", "102710", "KOSDAQ", List.of("이엔에프테크놀로지")),
            new StockDefinition("자화전자", "033240", "KOSPI", List.of("자화전자")),
            new StockDefinition("Tesla", "TSLA", "NASDAQ", List.of("테슬라", "Tesla", "TSLA")),
            new StockDefinition("NVIDIA", "NVDA", "NASDAQ", List.of("엔비디아", "NVIDIA", "NVDA", "젠슨 황")),
            new StockDefinition("Apple", "AAPL", "NASDAQ", List.of("애플", "Apple", "AAPL")),
            new StockDefinition("Microsoft", "MSFT", "NASDAQ", List.of("마이크로소프트", "Microsoft", "MSFT")),
            new StockDefinition("Meta", "META", "NASDAQ", List.of("메타", "Meta", "META")),
            new StockDefinition("Amazon", "AMZN", "NASDAQ", List.of("아마존", "Amazon", "AMZN")),
            new StockDefinition("Alphabet", "GOOGL", "NASDAQ", List.of("알파벳", "구글", "Alphabet", "Google", "GOOGL", "GOOG")),
            new StockDefinition("AMD", "AMD", "NASDAQ", List.of("AMD")),
            new StockDefinition("Broadcom", "AVGO", "NASDAQ", List.of("브로드컴", "Broadcom", "AVGO")),
            new StockDefinition("Oracle", "ORCL", "NYSE", List.of("오라클", "Oracle", "ORCL")),
            new StockDefinition("Dell Technologies", "DELL", "NYSE", List.of("Dell", "Dell Technologies", "DELL")),
            new StockDefinition("TSMC", "TSM", "NYSE", List.of("TSMC", "Taiwan Semiconductor", "TSM")),
            new StockDefinition("Intel", "INTC", "NASDAQ", List.of("인텔", "Intel", "INTC")),
            new StockDefinition("Fluence Energy", "FLNC", "NASDAQ", List.of("플루언스", "Fluence", "FLNC")),
            new StockDefinition("Strategy", "MSTR", "NASDAQ", List.of("스트레티지", "Strategy", "MicroStrategy", "MSTR")),
            new StockDefinition("JetBlue Airways", "JBLU", "NASDAQ", List.of("제트블루", "JetBlue", "JBLU")),
            new StockDefinition("Robinhood", "HOOD", "NASDAQ", List.of("로빈후드", "Robinhood", "HOOD")),
            new StockDefinition("BP", "BP", "NYSE", List.of("BP")),
            new StockDefinition("Voya Financial", "VOYA", "NYSE", List.of("보야", "Voya", "Voya Financial", "VOYA")),
            new StockDefinition("Edgewise Therapeutics", "EWTX", "NASDAQ", List.of("엣지와이스", "에지와이즈", "Edgewise", "EWTX")),
            new StockDefinition("Weatherford", "WFRD", "NASDAQ", List.of("웨더포드", "Weatherford", "WFRD")),
            new StockDefinition("Volkswagen", "VWAGY", "OTC", List.of("폭스바겐", "Volkswagen", "VWAGY")),
            new StockDefinition("Prosus", "PROSY", "OTC", List.of("Prosus", "PROSY", "PRX.AS")),
            new StockDefinition("Delivery Hero", "DHER.DE", "XETRA", List.of("Delivery Hero", "DHER.DE")),
            new StockDefinition("Honeywell", "HON", "NASDAQ", List.of("하니웰", "Honeywell", "HON")),
            new StockDefinition("Moderna", "MRNA", "NASDAQ", List.of("모더나", "Moderna", "MRNA")),
            new StockDefinition("MGM Resorts", "MGM", "NYSE", List.of("MGM 리조트", "MGM Resorts", "MGM")),
            new StockDefinition("Wise", "WISE.L", "LSE", List.of("핀테크 와이즈", "Wise", "WISE.L")),
            new StockDefinition("BYD", "BYDDY", "OTC", List.of("BYD", "비야디", "BYDDY"))
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
                    List.of("AI서버", "AI 서버", "데이터센터", "AI 인프라", "AI infrastructure"),
                    List.of(
                            new StockDefinition("NVIDIA", "NVDA", "NASDAQ"),
                            new StockDefinition("Dell Technologies", "DELL", "NYSE"),
                            new StockDefinition("Broadcom", "AVGO", "NASDAQ"),
                            new StockDefinition("AMD", "AMD", "NASDAQ"),
                            new StockDefinition("Oracle", "ORCL", "NYSE")
                    )
            ),
            new ThemeDefinition(
                    List.of("AI 열풍", "피지컬AI", "Physical AI"),
                    List.of(
                            new StockDefinition("NVIDIA", "NVDA", "NASDAQ"),
                            new StockDefinition("Microsoft", "MSFT", "NASDAQ"),
                            new StockDefinition("Alphabet", "GOOGL", "NASDAQ")
                    )
            ),
            new ThemeDefinition(
                    List.of("빅테크", "클라우드"),
                    List.of(
                            new StockDefinition("Microsoft", "MSFT", "NASDAQ"),
                            new StockDefinition("Amazon", "AMZN", "NASDAQ"),
                            new StockDefinition("Alphabet", "GOOGL", "NASDAQ"),
                            new StockDefinition("Meta", "META", "NASDAQ"),
                            new StockDefinition("Apple", "AAPL", "NASDAQ")
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

        for (CodeMatch match : telegramDisclosureCodeMatches(text)) {
            if (seenSymbols.add(match.symbol())) {
                mappedStocks.add(new MappedStock(match.name(), match.symbol(), "KRX", MATCH_TYPE_DIRECT));
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

    private List<CodeMatch> telegramDisclosureCodeMatches(String text) {
        List<CodeMatch> matches = new ArrayList<>();
        Matcher matcher = TELEGRAM_DISCLOSURE_CODE_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = normalizeDisclosureCompanyName(matcher.group(1));
            String symbol = matcher.group(2);
            if (!name.isBlank()) {
                matches.add(new CodeMatch(matcher.start(), name, symbol));
            }
        }
        matches.sort(Comparator.comparingInt(CodeMatch::index));
        return matches;
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

    private String normalizeDisclosureCompanyName(String value) {
        return value == null ? "" : value.trim();
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

    private record CodeMatch(int index, String name, String symbol) {
    }

    private record ThemeMatch(int index, int order, ThemeDefinition theme) {
    }
}
