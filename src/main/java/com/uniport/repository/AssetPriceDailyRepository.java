package com.uniport.repository;

import com.uniport.entity.AssetPriceDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetPriceDailyRepository extends JpaRepository<AssetPriceDaily, Long> {

    List<AssetPriceDaily> findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(String assetId,
                                                                              LocalDate startDate,
                                                                              LocalDate endDate);

    Optional<AssetPriceDaily> findByAssetIdAndTradeDate(String assetId, LocalDate tradeDate);

    long countByAssetId(String assetId);

    @Query("""
            select p.assetId as assetId,
                   min(p.tradeDate) as firstTradeDate,
                   max(p.tradeDate) as lastTradeDate,
                   count(p) as priceCount
            from AssetPriceDaily p
            where p.assetId in :assetIds
            group by p.assetId
            """)
    List<AssetPriceCoverageSummary> findCoverageSummariesByAssetIds(@Param("assetIds") Collection<String> assetIds);

    @Query("""
            select p.assetId as assetId,
                   min(p.tradeDate) as firstTradeDate,
                   max(p.tradeDate) as lastTradeDate,
                   count(p) as priceCount
            from AssetPriceDaily p
            where p.assetId = :assetId
            group by p.assetId
            """)
    Optional<AssetPriceCoverageSummary> findCoverageSummaryByAssetId(@Param("assetId") String assetId);

    interface AssetPriceCoverageSummary {
        String getAssetId();

        LocalDate getFirstTradeDate();

        LocalDate getLastTradeDate();

        Long getPriceCount();
    }
}
