package com.uniport.service.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DWS MST 한 행 파싱. KOSPI part2=228, KOSDAQ part2=222.
 * part1에서 codeRaw[0:9], stdCode[9:21], nameKr[21:].
 * code는 6자리로 정규화(trim, 1~5자리면 왼쪽 0 패딩).
 */
@Component
public class StockMasterParser {

    private static final Logger log = LoggerFactory.getLogger(StockMasterParser.class);

    private static final int KOSPI_PART2_LEN = 228;
    private static final int KOSDAQ_PART2_LEN = 222;
    private static final int CODE_RAW_LEN = 9;
    private static final int STD_CODE_START = 9;
    private static final int STD_CODE_END = 21;
    private static final int NAME_KR_START = 21;
    private static final int CODE_LEN = 6;

    /**
     * 한 행 파싱. 정규화 실패 시 null 반환하고 warn 로그.
     */
    public ParsedRow parseLine(String line, String market) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        int part2Len = "KOSDAQ".equalsIgnoreCase(market) ? KOSDAQ_PART2_LEN : KOSPI_PART2_LEN;
        if (line.length() < part2Len) {
            return null;
        }
        String part1 = line.substring(0, line.length() - part2Len);
        if (part1.length() < NAME_KR_START) {
            return null;
        }
        String codeRaw = safeSubstring(part1, 0, CODE_RAW_LEN).trim();
        String stdCode = safeSubstring(part1, STD_CODE_START, STD_CODE_END).trim();
        String nameKr = safeSubstring(part1, NAME_KR_START, part1.length()).trim();

        String code = normalizeCode(codeRaw);
        if (code == null) {
            log.warn("stock_master parse skip: codeRaw=[{}] market={}", codeRaw, market);
            return null;
        }
        return new ParsedRow(code, stdCode.isEmpty() ? null : stdCode, nameKr);
    }

    /**
     * codeRaw: 길이 6이면 그대로, 1~5면 왼쪽 0 패딩, 그 외 null.
     */
    public static String normalizeCode(String codeRaw) {
        if (codeRaw == null) return null;
        String t = codeRaw.trim();
        int len = t.length();
        if (len == CODE_LEN) {
            return t;
        }
        if (len >= 1 && len <= 5) {
            return String.format("%" + CODE_LEN + "s", t).replace(' ', '0');
        }
        return null;
    }

    private static String safeSubstring(String s, int start, int end) {
        if (s == null) return "";
        if (start >= s.length()) return "";
        int to = Math.min(end, s.length());
        return s.substring(start, to);
    }
}
