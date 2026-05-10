package com.uniport.service;

import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.StockMaster;
import com.uniport.repository.StockMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class StockVisualAssetResolver {

    private static final Logger log = LoggerFactory.getLogger(StockVisualAssetResolver.class);
    public static final String TYPE_FALLBACK_SYMBOL = "FALLBACK_SYMBOL";
    private static final String UNKNOWN_TEXT = "?";
    private static final SymbolColor NEUTRAL_COLOR = new SymbolColor("#E5E7EB", "#374151");
    private static final Pattern PARENTHESIS_TEXT = Pattern.compile("\\([^)]*\\)|（[^）]*）|\\[[^]]*]");
    private static final Pattern PREFERRED_SHARE_SUFFIX = Pattern.compile("(?i)\\d*우B?$");
    private static final List<String> ENGLISH_PREFIXES = List.of(
            "LG", "SK", "CJ", "KT", "KB", "NH", "DB", "BNK", "DGB", "POSCO", "NAVER", "S-OIL",
            "LS", "HMM", "OCI", "KCC", "LX", "GS", "HD", "HL", "JB", "BGF"
    );
    private static final List<String> KOREAN_PREFIXES = List.of(
            "삼성", "현대", "한화", "롯데", "신한", "하나", "우리", "미래", "한국", "두산",
            "셀트", "카카", "에코", "금양", "포스", "엔씨", "크래", "넷마"
    );
    private static final List<SymbolColor> PALETTE = List.of(
            new SymbolColor("#EEF2FF", "#4F46E5"),
            new SymbolColor("#E0F2FE", "#0284C7"),
            new SymbolColor("#DCFCE7", "#16A34A"),
            new SymbolColor("#FEF3C7", "#D97706"),
            new SymbolColor("#FFE4E6", "#E11D48"),
            new SymbolColor("#F3E8FF", "#9333EA"),
            new SymbolColor("#CCFBF1", "#0F766E"),
            new SymbolColor("#FEE2E2", "#DC2626"),
            NEUTRAL_COLOR,
            new SymbolColor("#DBEAFE", "#2563EB")
    );

    private final StockMasterRepository stockMasterRepository;

    public StockVisualAssetResolver(StockMasterRepository stockMasterRepository) {
        this.stockMasterRepository = stockMasterRepository;
    }

    public StockVisualDTO resolve(String market, String stockCode, String stockName, String logoUrl) {
        String lookupCode = normalizeStockCode(stockCode);
        Optional<StockMaster> master = lookupCode.isBlank()
                ? Optional.empty()
                : stockMasterRepository.findById(lookupCode);
        String receivedName = trim(stockName);
        String resolvedName = receivedName;
        String resolvedCode = firstNonBlank(lookupCode, trim(stockCode), normalizeStockName(receivedName), "UNKNOWN");
        String resolvedMarket = firstNonBlank(normalizeMarket(market), "UNKNOWN");

        if (master.isPresent()) {
            StockMaster stockMaster = master.get();
            resolvedName = firstNonBlank(stockMaster.getNameKr(), receivedName);
            resolvedCode = firstNonBlank(stockMaster.getCode(), resolvedCode);
            resolvedMarket = firstNonBlank(normalizeMarket(stockMaster.getMarket()), resolvedMarket);
            logInvalidMappingIfNeeded(market, resolvedCode, receivedName, stockMaster.getNameKr());
        } else if (!lookupCode.isBlank()) {
            log.info("event=UNKNOWN_STOCK market={} stockCode={} receivedStockName={}", normalizeMarket(market), lookupCode, receivedName);
        }

        boolean unknownWithoutName = master.isEmpty() && resolvedName.isBlank();
        SymbolColor color = unknownWithoutName ? NEUTRAL_COLOR : pickColor(resolvedMarket, resolvedCode);
        String text = unknownWithoutName ? UNKNOWN_TEXT : extractSymbolText(normalizeStockName(resolvedName));

        return StockVisualDTO.builder()
                .type(TYPE_FALLBACK_SYMBOL)
                .text(hasText(text) ? text : UNKNOWN_TEXT)
                .bgColor(color.bgColor())
                .textColor(color.textColor())
                .build();
    }

    String normalizeStockName(String stockName) {
        String normalized = PARENTHESIS_TEXT.matcher(trim(stockName)).replaceAll("");
        normalized = normalized.replaceAll("\\s+", "");
        return PREFERRED_SHARE_SUFFIX.matcher(normalized).replaceAll("");
    }

    String extractSymbolText(String normalizedStockName) {
        String normalized = trim(normalizedStockName);
        if (normalized.isBlank()) {
            return UNKNOWN_TEXT;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        for (String prefix : ENGLISH_PREFIXES) {
            if (upper.startsWith(prefix)) {
                return prefix;
            }
        }
        for (String prefix : KOREAN_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return prefix;
            }
        }
        String hangul = leadingHangul(normalized);
        if (!hangul.isBlank()) {
            return hangul;
        }
        String english = normalized.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!english.isBlank()) {
            return english.length() <= 5 ? english : english.substring(0, 3);
        }
        return UNKNOWN_TEXT;
    }

    private void logInvalidMappingIfNeeded(String market, String stockCode, String receivedName, String masterName) {
        if (!hasText(receivedName) || !hasText(masterName)) {
            return;
        }
        if (!normalizeStockName(receivedName).equalsIgnoreCase(normalizeStockName(masterName))) {
            log.warn("event=INVALID_STOCK_MAPPING market={} stockCode={} receivedStockName={} masterStockName={}",
                    normalizeMarket(market), stockCode, receivedName, masterName);
        }
    }

    private String leadingHangul(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().takeWhile(this::isHangul).limit(2).forEach(result::appendCodePoint);
        return result.toString();
    }

    private boolean isHangul(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HANGUL;
    }

    private SymbolColor pickColor(String market, String stockCode) {
        String input = normalizeMarketForHash(market) + ":" + firstNonBlank(stockCode, "UNKNOWN");
        long hash = 5381L;
        for (int i = 0; i < input.length(); i++) {
            hash = ((hash << 5) + hash) + input.charAt(i);
        }
        return PALETTE.get((int) Math.floorMod(hash, PALETTE.size()));
    }

    private String normalizeStockCode(String stockCode) {
        String code = trim(stockCode);
        String upper = code.toUpperCase(Locale.ROOT);
        if (upper.startsWith("KRX_")) {
            return code.substring(4);
        }
        if (upper.startsWith("US_")) {
            return code.substring(3);
        }
        return code;
    }

    private String normalizeMarket(String market) {
        return trim(market).toUpperCase(Locale.ROOT);
    }

    private String normalizeMarketForHash(String market) {
        String normalized = normalizeMarket(market);
        if (normalized.equals("KRX") || normalized.contains("KOS")) {
            return "KRX";
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record SymbolColor(String bgColor, String textColor) {
    }
}
