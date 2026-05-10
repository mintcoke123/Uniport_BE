package com.uniport.service.importer;

import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UsAssetMasterImporterService {

    private static final Logger log = LoggerFactory.getLogger(UsAssetMasterImporterService.class);
    private static final String ASSET_TYPE_STOCK = "STOCK";
    private static final String CURRENCY_USD = "USD";
    private static final String DATA_STATUS_PENDING = "PENDING_VERIFICATION";

    private final NasdaqSymbolDirectoryClient client;
    private final NasdaqSymbolDirectoryParser parser;
    private final AssetMasterRepository assetMasterRepository;

    public UsAssetMasterImporterService(NasdaqSymbolDirectoryClient client,
                                        NasdaqSymbolDirectoryParser parser,
                                        AssetMasterRepository assetMasterRepository) {
        this.client = client;
        this.parser = parser;
        this.assetMasterRepository = assetMasterRepository;
    }

    @Transactional
    public ImportResult importAll() {
        List<UsAssetMasterRow> parsedRows = new ArrayList<>();
        parsedRows.addAll(parser.parseNasdaqListed(client.downloadNasdaqListed()));
        parsedRows.addAll(parser.parseOtherListed(client.downloadOtherListed()));

        Map<String, UsAssetMasterRow> unique = new LinkedHashMap<>();
        for (UsAssetMasterRow row : parsedRows) {
            unique.putIfAbsent(row.assetId(), row);
        }

        int inserted = 0;
        int updated = 0;
        List<AssetMaster> toSave = new ArrayList<>();
        for (UsAssetMasterRow row : unique.values()) {
            Optional<AssetMaster> existing = assetMasterRepository.findByAssetIdAndActiveTrue(row.assetId());
            AssetMaster asset = existing.orElseGet(() -> AssetMaster.builder()
                    .assetId(row.assetId())
                    .build());
            if (existing.isPresent()) {
                updated++;
            } else {
                inserted++;
            }
            asset.setAssetType(ASSET_TYPE_STOCK);
            asset.setName(row.name());
            asset.setSymbol(row.symbol());
            asset.setMarket(row.market());
            asset.setCurrency(CURRENCY_USD);
            asset.setActive(true);
            if (asset.getBacktestEnabled() == null) {
                asset.setBacktestEnabled(false);
            }
            if (asset.getPriceSourceStatus() == null || asset.getPriceSourceStatus().isBlank()) {
                asset.setPriceSourceStatus(DATA_STATUS_PENDING);
            }
            toSave.add(asset);
        }
        if (!toSave.isEmpty()) {
            assetMasterRepository.saveAll(toSave);
        }
        ImportResult result = ImportResult.builder()
                .inserted(inserted)
                .updated(updated)
                .skipped(parsedRows.size() - unique.size())
                .build();
        log.info("us asset_master importAll done: inserted={} updated={} skipped={}",
                result.getInserted(), result.getUpdated(), result.getSkipped());
        return result;
    }
}
