package com.uniport.repository;

import com.uniport.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);
    Optional<PointTransaction> findBySourceTypeAndSourceId(String sourceType, String sourceId);
    void deleteByUser_Id(Long userId);
}
