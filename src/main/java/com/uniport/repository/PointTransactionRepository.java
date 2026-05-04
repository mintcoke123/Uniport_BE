package com.uniport.repository;

import com.uniport.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);
    void deleteByUser_Id(Long userId);
}
