package com.uniport.repository;

import com.uniport.entity.AssetAlias;
import com.uniport.entity.AssetMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssetAliasRepository extends JpaRepository<AssetAlias, Long> {

    @Query("""
            SELECT asset
            FROM AssetAlias alias
            JOIN alias.asset asset
            WHERE alias.active = true
              AND asset.active = true
              AND (:assetType IS NULL OR asset.assetType = :assetType)
              AND (
                    :market IS NULL
                    OR asset.market = :market
                    OR (:market = 'KRX' AND asset.market IN ('KRX', 'KOSPI', 'KOSDAQ'))
                    OR (:market = 'US' AND asset.market IN ('US', 'NASDAQ', 'NYSE', 'AMEX', 'NYSE_ARCA'))
              )
              AND (
                    :keyword = ''
                    OR LOWER(alias.alias) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY
              CASE
                WHEN LOWER(alias.alias) = LOWER(:keyword) THEN 0
                WHEN LOWER(alias.alias) LIKE LOWER(CONCAT(:keyword, '%')) THEN 1
                ELSE 2
              END,
              asset.assetType ASC,
              asset.market ASC,
              asset.name ASC
            """)
    List<AssetMaster> searchActiveAssetMatches(@Param("keyword") String keyword,
                                               @Param("assetType") String assetType,
                                               @Param("market") String market,
                                               Pageable pageable);
}
