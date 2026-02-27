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
}
