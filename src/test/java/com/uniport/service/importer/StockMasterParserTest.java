package com.uniport.service.importer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MST 파서 단위 테스트. 네트워크/파일 미사용.
 */
class StockMasterParserTest {

    private static final int KOSPI_PART2_LEN = 228;
    private static final int KOSDAQ_PART2_LEN = 222;

    private StockMasterParser parser;

    @BeforeEach
    void setUp() {
        parser = new StockMasterParser();
    }

    /**
     * KOSPI: part1 = row[0:len-228]. part1에서 [0:9]=codeRaw, [9:21]=stdCode, [21:]=nameKr.
     */
    @Test
    void parseLine_kospi_part1_파싱() {
        String part1 = "005930   KR7005930000삼성전자";
        String line = part1 + "x".repeat(KOSPI_PART2_LEN);
        ParsedRow row = parser.parseLine(line, "KOSPI");
        assertNotNull(row);
        assertEquals("005930", row.getCode());
        assertEquals("KR7005930000", row.getStdCode());
        assertEquals("삼성전자", row.getNameKr());
    }

    /**
     * KOSDAQ: part2 길이 222로 part1 구간이 다름. 동일 part1이면 KOSDAQ 라인은 222 filler.
     */
    @Test
    void parseLine_kosdaq_part2길이_반영() {
        String part1 = "005930   KR7005930000삼성전자";
        String line = part1 + "y".repeat(KOSDAQ_PART2_LEN);
        ParsedRow row = parser.parseLine(line, "KOSDAQ");
        assertNotNull(row);
        assertEquals("005930", row.getCode());
        assertEquals("KR7005930000", row.getStdCode());
        assertEquals("삼성전자", row.getNameKr());
    }

    @Test
    void normalizeCode_6자리_그대로() {
        assertEquals("005930", StockMasterParser.normalizeCode("005930"));
        assertEquals("000660", StockMasterParser.normalizeCode("000660   "));
    }

    @Test
    void normalizeCode_1to5자리_왼쪽0패딩() {
        assertEquals("005930", StockMasterParser.normalizeCode("5930"));
        assertEquals("000001", StockMasterParser.normalizeCode("1"));
    }

    @Test
    void normalizeCode_그외_skip() {
        assertNull(StockMasterParser.normalizeCode(""));
        assertNull(StockMasterParser.normalizeCode("1234567"));
    }
}
