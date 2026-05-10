package com.uniport.service;

import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.KisHistoricalPriceProvider;
import com.uniport.service.importer.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssetBacktestVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AssetBacktestVerificationService.class);
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final int LOOKBACK_DAYS = 45;
    private static final String ASSET_TYPE_BOND = "BOND";
    private static final String ASSET_TYPE_CASH = "CASH";
    private static final String DATA_STATUS_VERIFIED = "VERIFIED";
    private static final String DATA_STATUS_PROXY = "PROXY";
    private static final String DATA_STATUS_PRICE_UNAVAILABLE = "PRICE_UNAVAILABLE";

    private final AssetMasterRepository assetMasterRepository;
    private final HistoricalPriceProvider historicalPriceProvider;

    public AssetBacktestVerificationService(AssetMasterRepository assetMasterRepository,
                                            KisHistoricalPriceProvider historicalPriceProvider) {
        this.assetMasterRepository = assetMasterRepository;
        this.historicalPriceProvider = historicalPriceProvider;
    }

    @Transactional
    public ImportResult verifyActiveAssets(int batchSize) {
        int safeBatchSize = batchSize < 1 ? DEFAULT_BATCH_SIZE : Math.min(batchSize, MAX_BATCH_SIZE);
        List<AssetMaster> assets = assetMasterRepository.findByActiveTrue(PageRequest.of(0, safeBatchSize));
        if (assets.isEmpty()) {
            return ImportResult.empty();
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(LOOKBACK_DAYS);
        LocalDateTime verifiedAt = LocalDateTime.now();
        int enabled = 0;
        int disabled = 0;

        for (AssetMaster asset : assets) {
            asset.setLastPriceVerifiedAt(verifiedAt);
            if (isProxyAsset(asset)) {
                asset.setBacktestEnabled(true);
                asset.setPriceSourceStatus(DATA_STATUS_PROXY);
                asset.setLastPriceError(null);
                enabled++;
                continue;
            }
            try {
                List<BacktestPricePoint> series = historicalPriceProvider.getSecurityPriceSeries(
                        asset.getAssetId(),
                        startDate,
                        endDate
                );
                if (series != null && series.size() >= 2) {
                    asset.setBacktestEnabled(true);
                    asset.setPriceSourceStatus(DATA_STATUS_VERIFIED);
                    asset.setLastPriceError(null);
                    enabled++;
                } else {
                    markUnavailable(asset, "No recent price data from provider");
                    disabled++;
                }
            } catch (Exception e) {
                markUnavailable(asset, "Price provider error: " + safeMessage(e));
                disabled++;
                log.warn("[asset-backtest-verification] {} 검증 실패", asset.getAssetId(), e);
            }
        }

        assetMasterRepository.saveAll(assets);
        return ImportResult.builder()
                .inserted(0)
                .updated(enabled)
                .skipped(disabled)
                .build();
    }

    private void markUnavailable(AssetMaster asset, String reason) {
        asset.setBacktestEnabled(false);
        asset.setPriceSourceStatus(DATA_STATUS_PRICE_UNAVAILABLE);
        asset.setLastPriceError(truncate(reason, 500));
    }

    private boolean isProxyAsset(AssetMaster asset) {
        String type = asset.getAssetType();
        return ASSET_TYPE_BOND.equals(type) || ASSET_TYPE_CASH.equals(type);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
