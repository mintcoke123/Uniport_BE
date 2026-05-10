package com.uniport.repository;

import com.uniport.entity.StockMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockMasterRepository extends JpaRepository<StockMaster, String> {

    /**
     * name_kr 기준 ILIKE 포함 검색, name_kr asc 정렬.
     * limit/offset은 Pageable로 전달 (서비스 계층에서 clamp 등 처리).
     */
    @Query(value = "SELECT * FROM stock_master WHERE name_kr ILIKE CONCAT('%', :q, '%') ORDER BY name_kr ASC",
            nativeQuery = true)
    List<StockMaster> findByNameKrIlikeOrderByNameKrAsc(@Param("q") String q, Pageable pageable);

    @Query(value = """
            SELECT *
            FROM stock_master
            WHERE name_kr ILIKE CONCAT('%', :q, '%')
               OR code ILIKE CONCAT('%', :q, '%')
               OR market ILIKE CONCAT('%', :q, '%')
            ORDER BY
                CASE
                    WHEN code ILIKE :q THEN 0
                    WHEN name_kr ILIKE CONCAT(:q, '%') THEN 1
                    ELSE 2
                END,
                market ASC,
                name_kr ASC
            """,
            nativeQuery = true)
    List<StockMaster> searchForEtfAssetCandidates(@Param("q") String q, Pageable pageable);
}
