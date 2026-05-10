package com.uniport.service.importer;

import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsAssetMasterImporterServiceTest {

    @Test
    void importAll_upsertsNasdaqDirectoryRowsIntoAssetMaster() {
        NasdaqSymbolDirectoryClient client = mock(NasdaqSymbolDirectoryClient.class);
        AssetMasterRepository repository = mock(AssetMasterRepository.class);
        UsAssetMasterImporterService service = new UsAssetMasterImporterService(
                client,
                new NasdaqSymbolDirectoryParser(),
                repository
        );
        when(client.downloadNasdaqListed()).thenReturn("""
                Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
                AAPL|Apple Inc. - Common Stock|Q|N|N|40|N|N
                File Creation Time: 0509202618:03|||||||
                """);
        when(client.downloadOtherListed()).thenReturn("""
                ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
                A|Agilent Technologies, Inc. Common Stock|N|A|N|100|N|A
                File Creation Time: 0509202618:03|||||||
                """);
        when(repository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.empty());
        AssetMaster existing = AssetMaster.builder()
                .assetId("US_A")
                .assetType("STOCK")
                .name("Old")
                .symbol("A")
                .market("NASDAQ")
                .currency("USD")
                .active(true)
                .build();
        when(repository.findByAssetIdAndActiveTrue("US_A")).thenReturn(Optional.of(existing));

        ImportResult result = service.importAll();

        assertEquals(1, result.getInserted());
        assertEquals(1, result.getUpdated());
        assertEquals(0, result.getSkipped());
        verify(repository).saveAll(argThat(values -> {
            List<AssetMaster> saved = (List<AssetMaster>) values;
            return saved.size() == 2
                    && saved.stream().anyMatch(asset ->
                    "US_AAPL".equals(asset.getAssetId())
                            && "Apple Inc. - Common Stock".equals(asset.getName())
                            && "AAPL".equals(asset.getSymbol())
                            && "NASDAQ".equals(asset.getMarket())
                            && "STOCK".equals(asset.getAssetType())
                            && "USD".equals(asset.getCurrency())
                            && Boolean.TRUE.equals(asset.getActive()))
                    && saved.stream().anyMatch(asset ->
                    "US_A".equals(asset.getAssetId())
                            && "Agilent Technologies, Inc. Common Stock".equals(asset.getName())
                            && "NYSE".equals(asset.getMarket()));
        }));
    }

    @Test
    void importAll_truncatesSecurityNamesToAssetMasterColumnLimit() {
        NasdaqSymbolDirectoryClient client = mock(NasdaqSymbolDirectoryClient.class);
        AssetMasterRepository repository = mock(AssetMasterRepository.class);
        UsAssetMasterImporterService service = new UsAssetMasterImporterService(
                client,
                new NasdaqSymbolDirectoryParser(),
                repository
        );
        String longName = IntStream.range(0, 170)
                .mapToObj(i -> "A")
                .collect(Collectors.joining());
        when(client.downloadNasdaqListed()).thenReturn("""
                Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
                LONG|%s|Q|N|N|40|N|N
                File Creation Time: 0509202618:03|||||||
                """.formatted(longName));
        when(client.downloadOtherListed()).thenReturn("""
                ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
                File Creation Time: 0509202618:03|||||||
                """);
        when(repository.findByAssetIdAndActiveTrue("US_LONG")).thenReturn(Optional.empty());

        service.importAll();

        verify(repository).saveAll(argThat(values -> {
            List<AssetMaster> saved = (List<AssetMaster>) values;
            return saved.size() == 1
                    && "US_LONG".equals(saved.get(0).getAssetId())
                    && saved.get(0).getName().length() == 160;
        }));
    }
}
