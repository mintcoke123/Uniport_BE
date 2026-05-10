package com.uniport.service.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 코스피+코스닥 종목 마스터 Importer.
 * 아무도 자동 호출하지 않음. 실행 가능한 Service만 제공.
 */
@Service
public class StockMasterImporterService {

    private static final Logger log = LoggerFactory.getLogger(StockMasterImporterService.class);

    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
    private static final String MARKET_KOSPI = "KOSPI";
    private static final String MARKET_KOSDAQ = "KOSDAQ";
    private static final String ASSET_TYPE_STOCK = "STOCK";
    private static final String CURRENCY_KRW = "KRW";
    private static final String DATA_STATUS_PENDING = "PENDING_VERIFICATION";
    private static final Charset MST_CHARSET = mstCharset();

    private static Charset mstCharset() {
        for (String name : new String[] { "CP949", "MS949", "x-windows-949" }) {
            try { return Charset.forName(name); } catch (Exception ignored) { }
        }
        return Charset.defaultCharset();
    }
    private static final int BATCH_SIZE = 1000;
    private static final String UPSERT_SQL =
            "INSERT INTO stock_master(code, std_code, name_kr, market, updated_at) " +
            "VALUES (?, ?, ?, ?, now()) " +
            "ON CONFLICT (code) DO UPDATE SET " +
            "std_code = EXCLUDED.std_code, name_kr = EXCLUDED.name_kr, market = EXCLUDED.market, updated_at = now()";
    private static final String ASSET_UPSERT_SQL =
            "INSERT INTO asset_master(asset_id, asset_type, name, symbol, market, currency, active, " +
                    "backtest_enabled, price_source_status, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, true, false, ?, now(), now()) " +
            "ON CONFLICT (asset_id) DO UPDATE SET " +
                    "name = EXCLUDED.name, symbol = EXCLUDED.symbol, market = EXCLUDED.market, " +
                    "currency = EXCLUDED.currency, active = true, updated_at = now()";

    private final JdbcTemplate jdbcTemplate;
    private final DownloadClient downloadClient;
    private final ZipExtractor zipExtractor;
    private final StockMasterParser parser;

    public StockMasterImporterService(JdbcTemplate jdbcTemplate,
                                      DownloadClient downloadClient,
                                      ZipExtractor zipExtractor,
                                      StockMasterParser parser) {
        this.jdbcTemplate = jdbcTemplate;
        this.downloadClient = downloadClient;
        this.zipExtractor = zipExtractor;
        this.parser = parser;
    }

    public ImportResult importAll() throws Exception {
        ImportResult total = ImportResult.empty();
        try {
            ImportResult kospi = importKospi();
            total.add(kospi);
            ImportResult kosdaq = importKosdaq();
            total.add(kosdaq);
            log.info("stock_master importAll done: inserted={} updated={} skipped={}", total.getInserted(), total.getUpdated(), total.getSkipped());
            return total;
        } catch (Exception e) {
            log.error("stock_master importAll failed", e);
            throw e;
        }
    }

    public ImportResult importKospi() throws IOException {
        return importMarket(KOSPI_URL, MARKET_KOSPI);
    }

    public ImportResult importKosdaq() throws IOException {
        return importMarket(KOSDAQ_URL, MARKET_KOSDAQ);
    }

    private ImportResult importMarket(String zipUrl, String market) throws IOException {
        Path zipPath = null;
        Path workDir = null;
        try {
            zipPath = downloadClient.downloadToZipFile(zipUrl);
            workDir = Files.createTempDirectory("stock_master_import_");
            Path mstPath = zipExtractor.extractMst(zipPath, workDir);
            ParseResult parseResult = parseMstFile(mstPath, market);
            ImportResult result = upsertBatch(parseResult.rows, market);
            result.setSkipped(result.getSkipped() + parseResult.skipped);
            log.info("stock_master import {}: inserted={} updated={} skipped={}", market, result.getInserted(), result.getUpdated(), result.getSkipped());
            return result;
        } finally {
            if (zipPath != null) {
                try { Files.deleteIfExists(zipPath); } catch (IOException e) { log.warn("Failed to delete zip: {}", zipPath, e); }
            }
            if (workDir != null) {
                try {
                    Files.walk(workDir).sorted((a, b) -> b.compareTo(a)).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException e) { log.warn("Failed to delete workDir: {}", workDir, e); }
            }
        }
    }

    private static final class ParseResult {
        final List<ParsedRow> rows;
        final int skipped;
        ParseResult(List<ParsedRow> rows, int skipped) { this.rows = rows; this.skipped = skipped; }
    }

    private ParseResult parseMstFile(Path mstPath, String market) throws IOException {
        List<ParsedRow> list = new ArrayList<>();
        int skipped = 0;
        List<String> lines = Files.readAllLines(mstPath, MST_CHARSET);
        for (String line : lines) {
            ParsedRow row = parser.parseLine(line, market);
            if (row != null) list.add(row);
            else skipped++;
        }
        return new ParseResult(list, skipped);
    }

    private ImportResult upsertBatch(List<ParsedRow> rows, String market) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int to = Math.min(i + BATCH_SIZE, rows.size());
            List<ParsedRow> batch = rows.subList(i, to);
            List<String> codes = batch.stream().map(ParsedRow::getCode).collect(Collectors.toList());
            Set<String> existing = new HashSet<>();
            if (!codes.isEmpty()) {
                String inPlaceholders = codes.stream().map(c -> "?").collect(Collectors.joining(","));
                List<String> existingList = jdbcTemplate.queryForList(
                        "SELECT code FROM stock_master WHERE code IN (" + inPlaceholders + ")",
                        String.class,
                        codes.toArray()
                );
                existing.addAll(existingList);
            }
            jdbcTemplate.batchUpdate(UPSERT_SQL, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int idx) throws java.sql.SQLException {
                    ParsedRow r = batch.get(idx);
                    ps.setString(1, r.getCode());
                    ps.setString(2, r.getStdCode());
                    ps.setString(3, r.getNameKr());
                    ps.setString(4, market);
                }
                @Override
                public int getBatchSize() { return batch.size(); }
            });
            jdbcTemplate.batchUpdate(ASSET_UPSERT_SQL, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int idx) throws java.sql.SQLException {
                    ParsedRow r = batch.get(idx);
                    ps.setString(1, "KRX_" + r.getCode());
                    ps.setString(2, ASSET_TYPE_STOCK);
                    ps.setString(3, r.getNameKr());
                    ps.setString(4, r.getCode());
                    ps.setString(5, market);
                    ps.setString(6, CURRENCY_KRW);
                    ps.setString(7, DATA_STATUS_PENDING);
                }
                @Override
                public int getBatchSize() { return batch.size(); }
            });
            for (ParsedRow r : batch) {
                if (existing.contains(r.getCode())) updated++; else inserted++;
            }
        }
        return ImportResult.builder().inserted(inserted).updated(updated).skipped(skipped).build();
    }
}
