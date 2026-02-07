package com.uniport.repository;

import com.uniport.entity.Holding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUser_Id(Long userId);

    Optional<Holding> findByUser_IdAndStockCode(Long userId, String stockCode);

    void deleteByUser_Id(Long userId);
}
