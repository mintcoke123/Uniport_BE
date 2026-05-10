package com.uniport.repository;

import com.uniport.entity.AssetMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetMasterRepository extends JpaRepository<AssetMaster, String> {

    Optional<AssetMaster> findByAssetIdAndActiveTrue(String assetId);

    Optional<AssetMaster> findFirstBySymbolIgnoreCaseAndAssetTypeAndActiveTrue(String symbol, String assetType);

    List<AssetMaster> findByActiveTrue(Pageable pageable);

    @Query("""
            SELECT asset
            FROM AssetMaster asset
            WHERE asset.active = true
              AND (:assetType IS NULL OR asset.assetType = :assetType)
              AND (
                    :market IS NULL
                    OR asset.market = :market
                    OR (:market = 'KRX' AND asset.market IN ('KRX', 'KOSPI', 'KOSDAQ'))
                    OR (:market = 'US' AND asset.market IN ('US', 'NASDAQ', 'NYSE', 'AMEX'))
              )
              AND (
                    :keyword = ''
                    OR LOWER(asset.assetId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(asset.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(asset.symbol) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(asset.market) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(asset.currency) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY
              CASE
                WHEN LOWER(asset.symbol) = LOWER(:keyword) THEN 0
                WHEN LOWER(asset.assetId) = LOWER(:keyword) THEN 1
                WHEN LOWER(asset.name) = LOWER(:keyword) THEN 2
                WHEN LOWER(asset.symbol) LIKE LOWER(CONCAT(:keyword, '%')) THEN 3
                WHEN LOWER(asset.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 4
                ELSE 5
              END,
              asset.assetType ASC,
              asset.market ASC,
              asset.name ASC
            """)
    List<AssetMaster> searchActive(@Param("keyword") String keyword,
                                   @Param("assetType") String assetType,
                                   @Param("market") String market,
                                   Pageable pageable);
}
