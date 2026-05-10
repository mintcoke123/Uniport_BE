package com.uniport.service.importer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NasdaqSymbolDirectoryParserTest {

    private final NasdaqSymbolDirectoryParser parser = new NasdaqSymbolDirectoryParser();

    @Test
    void parseNasdaqListed_importsNonTestNonEtfRowsAsNasdaqStocks() {
        String text = """
                Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
                AAPL|Apple Inc. - Common Stock|Q|N|N|40|N|N
                AAPU|Direxion Daily AAPL Bull 2X ETF|G|N|N|100|Y|N
                ZTEST|Test Company - Common Stock|Q|Y|N|100|N|N
                File Creation Time: 0509202618:03|||||||
                """;

        List<UsAssetMasterRow> rows = parser.parseNasdaqListed(text);

        assertEquals(1, rows.size());
        assertEquals("US_AAPL", rows.get(0).assetId());
        assertEquals("AAPL", rows.get(0).symbol());
        assertEquals("Apple Inc. - Common Stock", rows.get(0).name());
        assertEquals("NASDAQ", rows.get(0).market());
    }

    @Test
    void parseOtherListed_importsNonTestNonEtfRowsWithExchangeMapping() {
        String text = """
                ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
                A|Agilent Technologies, Inc. Common Stock|N|A|N|100|N|A
                ACU|Acme United Corporation. Common Stock|A|ACU|N|100|N|ACU
                AAA|Alternative Access First Priority CLO Bond ETF|P|AAA|Y|100|N|AAA
                FAKE|Fake Test Security|N|FAKE|N|100|Y|FAKE
                File Creation Time: 0509202618:03|||||||
                """;

        List<UsAssetMasterRow> rows = parser.parseOtherListed(text);

        assertEquals(2, rows.size());
        assertEquals("US_A", rows.get(0).assetId());
        assertEquals("NYSE", rows.get(0).market());
        assertEquals("US_ACU", rows.get(1).assetId());
        assertEquals("AMEX", rows.get(1).market());
    }
}
