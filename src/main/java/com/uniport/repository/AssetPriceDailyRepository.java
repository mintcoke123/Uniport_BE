package com.uniport.repository;

import com.uniport.entity.AssetPriceDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetPriceDailyRepository extends JpaRepository<AssetPriceDaily, Long> {

    List<AssetPriceDaily> findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(String assetId,
                                                                              LocalDate startDate,
                                                                              LocalDate endDate);

    Optional<AssetPriceDaily> findByAssetIdAndTradeDate(String assetId, LocalDate tradeDate);
}
